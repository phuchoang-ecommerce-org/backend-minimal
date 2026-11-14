package com.phucle.ecommerce.inventory.application;

import com.phucle.ecommerce.catalog.api.OwnedCatalogReadPort;
import com.phucle.ecommerce.identity.api.SellerShopDirectory;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.inventory.domain.StockItem;
import com.phucle.ecommerce.inventory.domain.StaleStockVersionException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SellerStockCommandService implements SellerStockCommands {
    private final SellerShopDirectory shops;
    private final OwnedCatalogReadPort catalog;
    private final StockWritePort stock;
    SellerStockCommandService(SellerShopDirectory shops, OwnedCatalogReadPort catalog, StockWritePort stock) {
        this.shops = shops; this.catalog = catalog; this.stock = stock;
    }
    @Transactional
    @Override public Stock replace(TrustedActor actor, UUID variantId, Update update) {
        if (actor == null || !"SELLER".equals(actor.operationalRole())) throw new StockFailure("ROLE_DENIED");
        if (variantId == null || update == null || update.onHand() < 0 || update.expectedVersion() < 0)
            throw new IllegalArgumentException("Invalid stock edit");
        UUID shop = shops.shopIdForAccount(actor.accountId()).orElseThrow(() -> new StockFailure("RESOURCE_NOT_FOUND"));
        if (!catalog.ownsVariant(variantId, shop)) throw new StockFailure("RESOURCE_NOT_FOUND");
        SellerShopDirectory.Shop locked = shops.lockShopForStockWrite(actor.accountId())
                .orElseThrow(() -> new StockFailure("RESOURCE_NOT_FOUND"));
        if (!locked.id().equals(shop)) throw new StockFailure("RESOURCE_NOT_FOUND");
        if (!locked.active()) throw new StockFailure("SELLER_INACTIVE");
        StockItem current = stock.find(variantId);
        if (current == null) throw new StockFailure("RESOURCE_NOT_FOUND");
        StockItem replacement;
        try {
            replacement = current.replace(update.onHand(), update.expectedVersion());
        } catch (StaleStockVersionException stale) {
            throw new StockFailure("STALE_VERSION");
        }
        StockItem updated = stock.replace(current, replacement);
        if (updated == null) throw new StockFailure("STALE_VERSION");
        return new Stock(updated.variantId(), updated.onHand(), updated.version());
    }
}

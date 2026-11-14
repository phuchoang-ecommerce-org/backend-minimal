package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.SellerShopDirectory;
import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SellerShopActor {
    private final SellerShopDirectory shops;
    SellerShopActor(SellerShopDirectory shops) { this.shops = shops; }
    void requireSeller(TrustedActor actor) {
        if (actor == null || !"SELLER".equals(actor.operationalRole())) throw new OrderFailure("ROLE_DENIED");
    }
    Optional<UUID> shopIdFor(TrustedActor actor) { return shops.shopIdForAccount(actor.accountId()); }
}

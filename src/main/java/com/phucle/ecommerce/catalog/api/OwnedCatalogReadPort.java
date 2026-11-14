package com.phucle.ecommerce.catalog.api;

import java.util.UUID;

/** Owner-filtered Catalog facts for Seller read and Inventory command use cases. */
public interface OwnedCatalogReadPort {
    PublicCatalogReadPort.Product product(UUID productId, UUID sellerShopId);
    boolean ownsVariant(UUID variantId, UUID sellerShopId);
}

package com.phucle.ecommerce.inventory.domain;

import java.util.UUID;

/** An absolute stock edit is valid only for the version the Seller observed. */
public record StockItem(UUID variantId, int onHand, long version) {
    public StockItem {
        if (variantId == null || onHand < 0 || version < 0) throw new IllegalArgumentException("Invalid stock");
    }
    public StockItem replace(int quantity, long expectedVersion) {
        if (quantity < 0 || expectedVersion < 0) throw new IllegalArgumentException("Invalid stock edit");
        if (version != expectedVersion) throw new StaleStockVersionException();
        return new StockItem(variantId, quantity, Math.addExact(version, 1));
    }
}

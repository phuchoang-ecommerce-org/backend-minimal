package com.phucle.ecommerce.inventory.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PublicStockReadPort {
    record Stock(UUID variantId, int onHand, long version) {}
    Map<UUID, Stock> findStock(Set<UUID> variantIds);
}

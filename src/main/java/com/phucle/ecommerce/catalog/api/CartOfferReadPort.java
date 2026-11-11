package com.phucle.ecommerce.catalog.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CartOfferReadPort {
    record Offer(UUID variantId, BigDecimal priceUsd, boolean purchasable) {}
    Map<UUID, Offer> offers(Set<UUID> variantIds);
}

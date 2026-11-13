package com.phucle.ecommerce.catalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CheckoutOffers {
    record Offer(UUID productId, UUID sellerShopId, UUID variantId, String title, String sku,
                 BigDecimal unitPriceUsd, boolean purchasable) {}
    List<Offer> lockCurrent(List<UUID> variantIds);
}

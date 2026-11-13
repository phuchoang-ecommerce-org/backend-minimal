package com.phucle.ecommerce.ordering.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record OrderPlacementLine(UUID productId, UUID sellerId, UUID variantId, String productTitle, String sku,
                                 int quantity, BigDecimal unitPriceUsd, BigDecimal discountUsd) {
    public OrderPlacementLine {
        Objects.requireNonNull(sellerId);
        PurchasedLineSnapshot.priced(productId, sellerId, variantId, productTitle, sku,
                quantity, unitPriceUsd, discountUsd);
    }
}

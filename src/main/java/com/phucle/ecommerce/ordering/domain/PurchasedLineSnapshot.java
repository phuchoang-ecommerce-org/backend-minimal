package com.phucle.ecommerce.ordering.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PurchasedLineSnapshot(UUID productId, UUID sellerId, UUID variantId, String productTitle, String sku, int quantity,
                                    MoneyUsd unitPrice, MoneyUsd discount, MoneyUsd total) {
    public PurchasedLineSnapshot {
        Objects.requireNonNull(productId); Objects.requireNonNull(sellerId); Objects.requireNonNull(variantId);
        Objects.requireNonNull(unitPrice); Objects.requireNonNull(discount); Objects.requireNonNull(total);
        if (productTitle == null || productTitle.isBlank() || sku == null || sku.isBlank() || quantity <= 0
                || total.amount().compareTo(unitPrice.amount().multiply(BigDecimal.valueOf(quantity))
                .subtract(discount.amount())) != 0) throw new IllegalArgumentException("Invalid purchased line snapshot");
    }
    public static PurchasedLineSnapshot priced(UUID productId, UUID sellerId, UUID variantId, String productTitle, String sku,
                                                int quantity, BigDecimal unitPriceUsd, BigDecimal discountUsd) {
        MoneyUsd price = new MoneyUsd(unitPriceUsd);
        MoneyUsd discount = new MoneyUsd(discountUsd);
        MoneyUsd total = new MoneyUsd(price.amount().multiply(BigDecimal.valueOf(quantity))
                .subtract(discount.amount()));
        return new PurchasedLineSnapshot(productId, sellerId, variantId, productTitle, sku, quantity, price, discount, total);
    }
}

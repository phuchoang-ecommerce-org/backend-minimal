package com.phucle.ecommerce.ordering.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable, SellerShop-filtered Ordering projection. */
public interface SellerOrderReadPort {
    record Boundary(UUID id, Instant createdAt) {}
    record Line(UUID productId, UUID variantId, String productName, String sku, int quantity,
                BigDecimal unitPriceUsd, BigDecimal discountUsd, BigDecimal totalUsd) {}
    record Portion(UUID id, UUID sellerId, String paymentStatus, BigDecimal totalUsd, Instant createdAt,
                   UUID orderId, String status, List<Line> lines, Instant paidAt) {
        public Portion { lines = List.copyOf(lines); }
    }
    List<Boundary> pageOwned(UUID sellerId, Instant beforeTime, UUID beforeId, int fetchLimit);
    List<Portion> findOwnedAll(UUID sellerId, List<UUID> portionIds);
}

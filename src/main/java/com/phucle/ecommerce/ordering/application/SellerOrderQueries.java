package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SellerOrderQueries {
    record LineView(UUID productId, UUID variantId, String productName, String sku, int quantity,
                    BigDecimal unitPriceUsd, BigDecimal discountUsd, BigDecimal totalUsd) {}
    record OrderView(UUID id, UUID sellerId, String paymentStatus, BigDecimal totalUsd, Instant createdAt,
                     UUID orderId, String status, List<LineView> lines, Instant paidAt) {
        public OrderView { lines = List.copyOf(lines); }
    }
    record Page(List<OrderView> items, String nextCursor, boolean hasNext) {
        public Page { items = List.copyOf(items); }
    }
    Page page(TrustedActor actor, String cursor, int limit);
}

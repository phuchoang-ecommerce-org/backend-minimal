package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OwnOrderQueries {
    record AddressView(String recipient, String addressLine, String locality, String postalCode, String country) {}
    record PurchasedLineView(UUID productId, UUID variantId, String productName, String sku, int quantity,
                             BigDecimal unitPriceUsd, BigDecimal discountUsd, BigDecimal totalUsd) {}
    record SellerOrderView(UUID id, UUID sellerId, String paymentStatus, BigDecimal totalUsd, Instant createdAt,
                           UUID orderId, String status, List<PurchasedLineView> lines, Instant paidAt) {
        public SellerOrderView { lines = List.copyOf(lines); }
    }
    record OrderView(UUID id, UUID memberId, String status, String paymentStatus, BigDecimal totalUsd,
                     List<SellerOrderView> sellerOrders, Instant createdAt, AddressView address,
                     BigDecimal discountUsd, String paymentMethod, String voucherCode) {
        public OrderView { sellerOrders = List.copyOf(sellerOrders); }
    }
    record Page(List<OrderView> items, String nextCursor, boolean hasNext) {
        public Page { items = List.copyOf(items); }
    }
    OrderView detail(TrustedActor actor, UUID orderId);
    Page page(TrustedActor actor, String cursor, int limit);
}

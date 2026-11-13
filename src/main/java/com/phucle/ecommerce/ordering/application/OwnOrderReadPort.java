package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only, Member-filtered projections owned by Ordering. */
public interface OwnOrderReadPort {
    record Boundary(UUID id, Instant createdAt) {}
    record PurchasedLine(UUID productId, UUID variantId, String productName, String sku, int quantity,
                         BigDecimal unitPriceUsd, BigDecimal discountUsd, BigDecimal totalUsd) {}
    record SellerOrder(UUID id, UUID sellerId, String paymentStatus, BigDecimal totalUsd, Instant createdAt,
                       UUID orderId, String status, List<PurchasedLine> lines, Instant paidAt) {}
    record Order(UUID id, UUID memberId, String status, String paymentStatus, BigDecimal totalUsd,
                 List<SellerOrder> sellerOrders, Instant createdAt, AddressSnapshot address,
                 BigDecimal discountUsd, String paymentMethod, String voucherCode) {}
    List<Order> findOwnedAll(UUID memberId, List<UUID> orderIds);
    List<Boundary> pageOwned(UUID memberId, Instant beforeTime, UUID beforeId, int fetchLimit);
}

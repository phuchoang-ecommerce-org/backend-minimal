package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface CreationSnapshotReadPort {
    record PurchasedLine(UUID productId, UUID variantId, String productName, String sku, int quantity,
                         BigDecimal unitPriceUsd, BigDecimal discountUsd, BigDecimal totalUsd) {}
    record SellerOrder(UUID id, UUID sellerId, String paymentStatus, BigDecimal totalUsd, Instant createdAt,
                       UUID orderId, String status, List<PurchasedLine> lines, Instant paidAt) {}
    record Order(UUID id, UUID memberId, String status, String paymentStatus, BigDecimal totalUsd,
                 List<SellerOrder> sellerOrders, Instant createdAt, AddressSnapshot address,
                 BigDecimal discountUsd, String paymentMethod, String voucherCode) {}
    Order readCreation(UUID orderId, String voucherCode);
}

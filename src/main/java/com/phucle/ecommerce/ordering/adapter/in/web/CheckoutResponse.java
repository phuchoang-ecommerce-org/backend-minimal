package com.phucle.ecommerce.ordering.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record CheckoutResponse(UUID id, UUID memberId, String status, String paymentStatus, String totalUsd,
                        List<SellerOrderResponse> sellerOrders, Instant createdAt, AddressResponse address,
                        String discountUsd, String paymentMethod, String voucherCode) {
    record AddressResponse(String recipient, String addressLine, String locality, String postalCode, String country) {}
    record SellerOrderResponse(UUID id, UUID sellerId, String paymentStatus, String totalUsd, Instant createdAt,
                               UUID orderId, String status, List<PurchasedLineResponse> lines, Instant paidAt) {}
    record PurchasedLineResponse(UUID productId, UUID variantId, String productName, String sku, int quantity,
                                 String unitPriceUsd, String discountUsd, String totalUsd) {}
}

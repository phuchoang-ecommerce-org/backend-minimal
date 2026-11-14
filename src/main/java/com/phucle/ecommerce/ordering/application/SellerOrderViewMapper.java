package com.phucle.ecommerce.ordering.application;

final class SellerOrderViewMapper {
    private SellerOrderViewMapper() {}
    static SellerOrderQueries.OrderView toView(SellerOrderReadPort.Portion portion) {
        return new SellerOrderQueries.OrderView(portion.id(), portion.sellerId(), portion.paymentStatus(),
                portion.totalUsd(), portion.createdAt(), portion.orderId(), portion.status(),
                portion.lines().stream().map(line -> new SellerOrderQueries.LineView(line.productId(),
                        line.variantId(), line.productName(), line.sku(), line.quantity(), line.unitPriceUsd(),
                        line.discountUsd(), line.totalUsd())).toList(), portion.paidAt());
    }
}

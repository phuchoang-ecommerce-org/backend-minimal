package com.phucle.ecommerce.ordering.application;

import java.util.List;

/** Converts immutable Ordering read facts to the application input-port view. */
final class OwnOrderViewMapper {
    private OwnOrderViewMapper() {}

    static OwnOrderQueries.OrderView toView(OwnOrderReadPort.Order order) {
        var address = order.address();
        var addressView = new OwnOrderQueries.AddressView(address.recipient(), address.addressLine(),
                address.locality(), address.postalCode(), address.country());
        List<OwnOrderQueries.SellerOrderView> portions = order.sellerOrders().stream().map(portion -> {
            List<OwnOrderQueries.PurchasedLineView> lines = portion.lines().stream().map(line ->
                    new OwnOrderQueries.PurchasedLineView(line.productId(), line.variantId(), line.productName(),
                            line.sku(), line.quantity(), line.unitPriceUsd(), line.discountUsd(), line.totalUsd())).toList();
            return new OwnOrderQueries.SellerOrderView(portion.id(), portion.sellerId(), portion.paymentStatus(),
                    portion.totalUsd(), portion.createdAt(), portion.orderId(), portion.status(), lines, portion.paidAt());
        }).toList();
        return new OwnOrderQueries.OrderView(order.id(), order.memberId(), order.status(), order.paymentStatus(),
                order.totalUsd(), portions, order.createdAt(), addressView, order.discountUsd(),
                order.paymentMethod(), order.voucherCode());
    }
}

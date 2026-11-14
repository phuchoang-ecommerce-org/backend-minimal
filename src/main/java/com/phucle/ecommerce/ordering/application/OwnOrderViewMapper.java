package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import java.util.List;

/** Converts immutable Ordering read facts to the application input-port view. */
final class OwnOrderViewMapper {
    private OwnOrderViewMapper() {}

    static OwnOrderQueries.OrderView toView(OwnOrderReadPort.Order order) {
        var portions = order.sellerOrders().stream().map(OwnOrderViewMapper::toPortionView).toList();
        return new OwnOrderQueries.OrderView(order.id(), order.memberId(), order.status(), order.paymentStatus(),
                order.totalUsd(), portions, order.createdAt(), toAddressView(order.address()), order.discountUsd(),
                order.paymentMethod(), order.voucherCode());
    }

    private static OwnOrderQueries.AddressView toAddressView(AddressSnapshot address) {
        return new OwnOrderQueries.AddressView(address.recipient(), address.addressLine(),
                address.locality(), address.postalCode(), address.country());
    }

    private static OwnOrderQueries.SellerOrderView toPortionView(OwnOrderReadPort.SellerOrder portion) {
        List<OwnOrderQueries.PurchasedLineView> lines = portion.lines().stream()
                .map(OwnOrderViewMapper::toLineView).toList();
        return new OwnOrderQueries.SellerOrderView(portion.id(), portion.sellerId(), portion.paymentStatus(),
                portion.totalUsd(), portion.createdAt(), portion.orderId(), portion.status(), lines, portion.paidAt());
    }

    private static OwnOrderQueries.PurchasedLineView toLineView(OwnOrderReadPort.PurchasedLine line) {
        return new OwnOrderQueries.PurchasedLineView(line.productId(), line.variantId(), line.productName(),
                line.sku(), line.quantity(), line.unitPriceUsd(), line.discountUsd(), line.totalUsd());
    }
}

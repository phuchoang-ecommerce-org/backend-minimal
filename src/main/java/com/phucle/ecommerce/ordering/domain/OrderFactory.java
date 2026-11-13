package com.phucle.ecommerce.ordering.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Assembles a placed aggregate from transaction-protected checkout facts. */
public final class OrderFactory {
    private OrderFactory() {}

    public static CustomerOrder place(UUID memberId, AddressSnapshot address, List<OrderPlacementLine> lines) {
        if (memberId == null || address == null || lines == null || lines.isEmpty())
            throw new IllegalArgumentException("Empty checkout");
        Map<UUID, List<OrderLine>> bySeller = new LinkedHashMap<>();
        for (OrderPlacementLine line : lines) {
            if (line == null) throw new IllegalArgumentException("Invalid seller line");
            bySeller.computeIfAbsent(line.sellerId(), ignored -> new ArrayList<>())
                    .add(OrderLine.create(PurchasedLineSnapshot.priced(line.productId(), line.sellerId(), line.variantId(),
                            line.productTitle(), line.sku(), line.quantity(), line.unitPriceUsd(), line.discountUsd())));
        }
        List<SellerOrder> portions = bySeller.entrySet().stream()
                .map(entry -> SellerOrder.pending(entry.getKey(), entry.getValue())).toList();
        return CustomerOrder.placed(memberId, address, portions);
    }
}

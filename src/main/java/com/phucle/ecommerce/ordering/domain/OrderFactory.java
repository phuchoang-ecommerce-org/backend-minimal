package com.phucle.ecommerce.ordering.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Compile-only checkout seam for the Sprint 1 RED contract. */
public final class OrderFactory {
    private OrderFactory() {
    }

    public record Line(UUID productId, UUID sellerId, UUID variantId, String productTitle, String sku,
                       int quantity, BigDecimal unitPriceUsd, BigDecimal discountUsd) {
    }

    public record AddressSnapshot(String recipient, String addressLine, String locality, String postalCode, String country) {
    }

    public record PurchasedLine(Line source, BigDecimal lineTotalUsd) {
    }

    public record Portion(UUID sellerId, String paymentStatus, BigDecimal totalUsd, List<PurchasedLine> lines) {
        public Portion { lines = List.copyOf(lines); }
    }

    public record Draft(UUID memberId, String status, AddressSnapshot address, BigDecimal totalUsd, List<Portion> portions) {
        public Draft { portions = List.copyOf(portions); }
    }

    public static Draft place(UUID memberId, AddressSnapshot address, List<Line> lines) {
        throw new UnsupportedOperationException("PB-006 checkout awaits Sprint 2 implementation");
    }
}

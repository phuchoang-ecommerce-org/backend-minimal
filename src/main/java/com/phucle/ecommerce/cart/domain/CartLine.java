package com.phucle.ecommerce.cart.domain;

import java.util.UUID;

public record CartLine(UUID variantId, Quantity quantity) {
    public CartLine { if (variantId == null || quantity == null) throw new IllegalArgumentException(); }
}

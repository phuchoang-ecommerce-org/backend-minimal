package com.phucle.ecommerce.cart.domain;

public record Quantity(int value) {
    public Quantity { if (value <= 0) throw new IllegalArgumentException("Quantity must be positive"); }
    public Quantity plus(Quantity other) {
        return new Quantity(Math.addExact(value, other.value));
    }
}

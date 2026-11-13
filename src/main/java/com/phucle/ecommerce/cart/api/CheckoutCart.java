package com.phucle.ecommerce.cart.api;

import java.util.List;
import java.util.UUID;

public interface CheckoutCart {
    record Line(UUID variantId, int quantity) {}
    record Locked(UUID id, long version, List<Line> lines) {
        public Locked { lines = List.copyOf(lines); }
    }
    Locked lockOwned(UUID memberId);
    void consume(UUID cartId, long expectedVersion);
}

package com.phucle.ecommerce.cart.application.port.out;

import java.util.List;
import java.util.UUID;

public interface CartReadPort {
    Snapshot member(UUID memberId);
    Snapshot guest(String sessionHash);
    record Snapshot(long version, boolean consumed, List<Line> lines) {
        public Snapshot { lines = List.copyOf(lines); }
    }
    record Line(UUID variantId, int quantity) {}
}

package com.phucle.ecommerce.cart.application.port.in;

import java.util.List;
import java.util.UUID;

public record CartView(List<Line> lines, String totalUsd, long version) {
    public CartView { lines = List.copyOf(lines); }
    public record Line(UUID variantId, int quantity, String unitPriceUsd) {}
}

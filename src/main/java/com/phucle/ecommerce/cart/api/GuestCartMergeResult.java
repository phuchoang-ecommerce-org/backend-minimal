package com.phucle.ecommerce.cart.api;

import java.util.List;
import java.util.UUID;

public record GuestCartMergeResult(boolean merged, List<RejectedGuestCartLine> rejectedLines) {

    public record RejectedGuestCartLine(UUID variantId, String code) {
    }
}

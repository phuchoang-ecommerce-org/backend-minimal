package com.phucle.ecommerce.cart.api;

import java.util.List;
import java.util.UUID;

public record GuestCartMergeResult(boolean merged, boolean sourceConsumed, List<RejectedGuestCartLine> rejectedLines) {

    public record RejectedGuestCartLine(UUID variantId, String code) {
    }
}

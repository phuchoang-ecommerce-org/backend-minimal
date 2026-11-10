package com.phucle.ecommerce.cart.api;

import java.util.UUID;

public record GuestCartMergeCommand(UUID memberAccountId, String guestSessionProof) {
}

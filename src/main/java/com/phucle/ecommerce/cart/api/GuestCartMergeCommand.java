package com.phucle.ecommerce.cart.api;

import com.phucle.ecommerce.identity.api.TrustedActor;

public record GuestCartMergeCommand(TrustedActor actor, String guestSessionProof) {
}

package com.phucle.ecommerce.cart.application.port.in;

import java.util.UUID;
import com.phucle.ecommerce.identity.api.TrustedActor;

public interface CartCommands {
    GuestCart getOrCreateGuest(String proof);
    CartView setLine(TrustedActor actor, String guestProof, UUID variantId, int quantity, long expectedVersion);
    void removeLine(TrustedActor actor, String guestProof, UUID variantId, long expectedVersion);
}

package com.phucle.ecommerce.cart.application.port.in;

import java.util.UUID;

public interface CartCommands {
    GuestCart getOrCreateGuest(String proof);
    CartView setLine(UUID memberId, String guestProof, UUID variantId, int quantity, long expectedVersion);
    void removeLine(UUID memberId, String guestProof, UUID variantId, long expectedVersion);
}

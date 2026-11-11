package com.phucle.ecommerce.cart.application.port.out;

import com.phucle.ecommerce.cart.domain.Cart;
import java.time.Instant;
import java.util.UUID;

public interface CartRepositoryPort {
    Cart guest(String sessionHash, boolean lock);
    Cart member(UUID memberId, boolean lock);
    Cart createGuest(String sessionHash, Instant expiry);
    Cart createMember(UUID memberId);
    void save(Cart cart, long previousVersion);
}

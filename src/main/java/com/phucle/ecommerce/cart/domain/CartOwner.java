package com.phucle.ecommerce.cart.domain;

import java.util.UUID;

public sealed interface CartOwner permits CartOwner.Guest, CartOwner.Member {
    record Guest(String sessionHash) implements CartOwner {
        public Guest { if (sessionHash == null || sessionHash.isBlank()) throw new IllegalArgumentException(); }
    }
    record Member(UUID accountId) implements CartOwner {
        public Member { if (accountId == null) throw new IllegalArgumentException(); }
    }
}

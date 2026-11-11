package com.phucle.ecommerce.cart.api;

public interface GuestOriginPolicy {
    void requireAllowed(String origin);
    boolean secureCookie();
    long guestTtlSeconds();
}

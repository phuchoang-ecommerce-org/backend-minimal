package com.phucle.ecommerce.cart;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public final class CartSettings implements com.phucle.ecommerce.cart.api.GuestOriginPolicy,
        com.phucle.ecommerce.cart.application.port.out.GuestSessionPolicy {
    private final long ttlSeconds;
    private final Set<String> allowedOrigins;
    private final boolean secure;
    public CartSettings(@Value("${marketplace.cart.guest-ttl-seconds}") long ttlSeconds,
            @Value("${marketplace.cart.allowed-origins:}") String origins, Environment environment) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("Guest TTL must be positive");
        this.ttlSeconds = ttlSeconds;
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.secure = !environment.acceptsProfiles(Profiles.of("dev"));
    }
    public long ttlSeconds() { return ttlSeconds; }
    public boolean secure() { return secure; }
    @Override public boolean secureCookie() { return secure; }
    @Override public long guestTtlSeconds() { return ttlSeconds; }
    @Override public void requireAllowed(String origin) { requireOrigin(origin); }
    public void requireOrigin(String origin) {
        if (origin == null || !allowedOrigins.contains(origin))
            throw new com.phucle.ecommerce.cart.domain.CartFailure(
                    com.phucle.ecommerce.cart.domain.CartFailure.Code.ORIGIN_DENIED);
    }
}

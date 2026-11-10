package com.phucle.ecommerce.identity.adapter.out.security;

public record JwtSigningSettings(String issuer, String audience, long accessTokenTtlSeconds) {
}

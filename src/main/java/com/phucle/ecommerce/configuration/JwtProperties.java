package com.phucle.ecommerce.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketplace.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        long accessTokenTtlSeconds,
        String privateKey,
        String publicKey) {
}

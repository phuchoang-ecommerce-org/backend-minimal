package com.phucle.ecommerce.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketplace.dev-fixtures")
public record DevFixtureProperties(
        boolean enabled,
        String memberPasswordHash,
        String sellerPasswordHash,
        String adminPasswordHash) {
}

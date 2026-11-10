package com.phucle.ecommerce.identity.application;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}

package com.phucle.ecommerce.identity.api;

public record LoginResult(String accessToken, long expiresInSeconds, AccountView account) {
}

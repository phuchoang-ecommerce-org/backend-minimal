package com.phucle.ecommerce.identity.application;

public interface CredentialVerifier {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);
}

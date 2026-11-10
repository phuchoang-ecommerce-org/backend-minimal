package com.phucle.ecommerce.identity.adapter.out.security;

import com.phucle.ecommerce.identity.application.CredentialVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptCredentialVerifier implements CredentialVerifier {
    private final PasswordEncoder encoder;
    BCryptCredentialVerifier(PasswordEncoder encoder) { this.encoder = encoder; }
    public String hash(String rawPassword) { return encoder.encode(rawPassword); }
    public boolean matches(String rawPassword, String hash) { return encoder.matches(rawPassword, hash); }
}

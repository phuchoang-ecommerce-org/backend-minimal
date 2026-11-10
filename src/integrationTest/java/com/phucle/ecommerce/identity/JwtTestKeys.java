package com.phucle.ecommerce.identity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class JwtTestKeys {

    private static final KeyPair KEY_PAIR = generate();

    private JwtTestKeys() {
    }

    public static String privateKey() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
    }

    public static String publicKey() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot generate test JWT key pair", exception);
        }
    }
}

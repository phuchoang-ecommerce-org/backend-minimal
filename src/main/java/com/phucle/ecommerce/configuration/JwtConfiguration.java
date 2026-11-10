package com.phucle.ecommerce.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.phucle.ecommerce.identity.adapter.out.security.JwtSigningSettings;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
class JwtConfiguration {

    @Bean
    JwtSigningSettings jwtSigningSettings(JwtProperties properties) {
        return new JwtSigningSettings(
                required(properties.issuer(), "issuer"),
                required(properties.audience(), "audience"),
                positive(properties.accessTokenTtlSeconds()));
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = publicKey(properties);
        RSAPrivateKey privateKey = privateKey(properties);
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("marketplace-rs256").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(properties))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> issuer =
                JwtValidators.createDefaultWithIssuer(required(properties.issuer(), "issuer"));
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audience = jwt ->
                jwt.getAudience().contains(required(properties.audience(), "audience"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    private RSAPublicKey publicKey(JwtProperties properties) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required(properties.publicKey(), "public key"));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid marketplace JWT public key", exception);
        }
    }

    private RSAPrivateKey privateKey(JwtProperties properties) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required(properties.privateKey(), "private key"));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid marketplace JWT private key", exception);
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing marketplace JWT " + name);
        }
        return value;
    }

    private long positive(long value) {
        if (value < 1) {
            throw new IllegalStateException("Marketplace JWT access-token TTL must be positive");
        }
        return value;
    }
}

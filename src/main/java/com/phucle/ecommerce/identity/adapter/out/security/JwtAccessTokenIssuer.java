package com.phucle.ecommerce.identity.adapter.out.security;

import com.phucle.ecommerce.identity.application.AccessTokenIssuer;
import com.phucle.ecommerce.identity.application.IssuedAccessToken;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private static final Set<String> ROLES = Set.of("MEMBER", "SELLER", "ADMIN");

    private final JwtEncoder jwtEncoder;
    private final JwtSigningSettings settings;
    private final Clock clock;

    @Autowired
    JwtAccessTokenIssuer(JwtEncoder jwtEncoder, JwtSigningSettings settings) {
        this(jwtEncoder, settings, Clock.systemUTC());
    }

    JwtAccessTokenIssuer(JwtEncoder jwtEncoder, JwtSigningSettings settings, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(UUID accountId, String persistedRole) {
        if (!ROLES.contains(persistedRole)) {
            throw new IllegalArgumentException("role");
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(accountId.toString())
                .audience(List.of(settings.audience()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(settings.accessTokenTtlSeconds()))
                .claim("roles", List.of(persistedRole))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("marketplace-rs256").build();
        return new IssuedAccessToken(
                jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                settings.accessTokenTtlSeconds());
    }
}

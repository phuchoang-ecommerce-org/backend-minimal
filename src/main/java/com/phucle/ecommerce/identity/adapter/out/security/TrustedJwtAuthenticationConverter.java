package com.phucle.ecommerce.identity.adapter.out.security;

import com.phucle.ecommerce.identity.application.IdentityAccountRepository;
import com.phucle.ecommerce.identity.domain.Account;
import com.phucle.ecommerce.identity.domain.AccountId;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public final class TrustedJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
    private final IdentityAccountRepository accounts;

    public TrustedJwtAuthenticationConverter(IdentityAccountRepository accounts) { this.accounts = accounts; }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        UUID id = accountId(jwt);
        String role = roleClaim(jwt);
        Account account = activeAccount(id, role);
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())), id.toString());
    }

    private UUID accountId(Jwt jwt) {
        UUID id;
        try { id = UUID.fromString(jwt.getSubject()); }
        catch (RuntimeException exception) { throw new BadCredentialsException("Invalid account identity"); }
        return id;
    }

    private String roleClaim(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.size() != 1) throw new BadCredentialsException("Invalid operational role");
        return roles.getFirst();
    }

    private Account activeAccount(UUID id, String role) {
        Account account = accounts.findById(new AccountId(id))
                .orElseThrow(() -> new BadCredentialsException("Invalid account identity"));
        if (!account.active() || !account.role().name().equals(role))
            throw new BadCredentialsException("Invalid account identity");
        return account;
    }
}

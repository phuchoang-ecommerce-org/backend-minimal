package com.phucle.ecommerce.identity.adapter.out.security;

import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.identity.api.TrustedActor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class SecurityCurrentActor implements CurrentActor {
    @Override
    public TrustedActor require(HttpServletRequest request) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token))
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        String authority = token.getAuthorities().stream().findFirst()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Operational role is required"))
                .getAuthority();
        if (!authority.startsWith("ROLE_"))
            throw new AuthenticationCredentialsNotFoundException("Operational role is required");
        String role = authority.substring("ROLE_".length());
        Object correlation = request.getAttribute("correlationId");
        if (correlation == null) {
            correlation = UUID.randomUUID().toString();
            request.setAttribute("correlationId", correlation);
        }
        return new TrustedActor(UUID.fromString(token.getName()), role, correlation.toString());
    }
}

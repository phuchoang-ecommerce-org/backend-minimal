package com.phucle.ecommerce.identity.adapter.out.security;

import com.phucle.ecommerce.identity.api.CurrentActor;
import com.phucle.ecommerce.identity.api.TrustedActor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class SecurityCurrentActor implements CurrentActor {
    @Override
    public TrustedActor require() {
        JwtAuthenticationToken token = authenticatedToken();
        String role = operationalRole(token);
        String correlation = correlationId();
        return new TrustedActor(UUID.fromString(token.getName()), role, correlation);
    }

    private JwtAuthenticationToken authenticatedToken() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token))
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        return token;
    }

    private String operationalRole(JwtAuthenticationToken token) {
        String authority = token.getAuthorities().stream().findFirst()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Operational role is required"))
                .getAuthority();
        if (!authority.startsWith("ROLE_"))
            throw new AuthenticationCredentialsNotFoundException("Operational role is required");
        return authority.substring("ROLE_".length());
    }

    private String correlationId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
        Object correlation = request == null ? null : request.getAttribute("correlationId");
        if (correlation == null) correlation = UUID.randomUUID().toString();
        if (request != null) request.setAttribute("correlationId", correlation);
        return correlation.toString();
    }
}

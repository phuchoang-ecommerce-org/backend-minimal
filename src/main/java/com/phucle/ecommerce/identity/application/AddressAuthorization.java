package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import org.springframework.security.access.AccessDeniedException;

final class AddressAuthorization {
    private AddressAuthorization() {}
    static void requireMember(TrustedActor actor) {
        if (actor == null || !"MEMBER".equals(actor.operationalRole()))
            throw new AccessDeniedException("Member required");
    }
}

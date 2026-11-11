package com.phucle.ecommerce.cart.application;

import com.phucle.ecommerce.cart.domain.CartFailure;
import com.phucle.ecommerce.identity.api.TrustedActor;

final class CartActorAuthorization {
    private CartActorAuthorization() {
    }

    static void requireMember(TrustedActor actor) {
        if (actor == null || !"MEMBER".equals(actor.operationalRole()))
            throw new CartFailure(CartFailure.Code.ROLE_DENIED);
    }
}

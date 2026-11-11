package com.phucle.ecommerce.cart.application.port.in;

import com.phucle.ecommerce.identity.api.TrustedActor;

public interface CartQueries {
    CartView getMemberCart(TrustedActor actor);
}

package com.phucle.ecommerce.cart.application.port.in;

import java.util.UUID;

public interface CartQueries {
    CartView getMemberCart(UUID memberId);
}

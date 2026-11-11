package com.phucle.ecommerce.cart.application;

import com.phucle.ecommerce.cart.application.port.in.CartView;

interface GuestCartProjection {
    CartView existingGuest(String sessionHash);
}

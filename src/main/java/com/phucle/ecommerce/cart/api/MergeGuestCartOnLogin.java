package com.phucle.ecommerce.cart.api;

public interface MergeGuestCartOnLogin {

    GuestCartMergeResult merge(GuestCartMergeCommand command);
}

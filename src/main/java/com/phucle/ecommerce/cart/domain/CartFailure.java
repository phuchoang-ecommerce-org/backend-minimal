package com.phucle.ecommerce.cart.domain;

public final class CartFailure extends RuntimeException {
    public enum Code { AUTHENTICATION_REQUIRED, ROLE_DENIED, ORIGIN_DENIED, RESOURCE_NOT_FOUND, STALE_VERSION,
        ITEM_UNAVAILABLE, QUANTITY_EXCEEDED }
    private final Code code;
    public CartFailure(Code code) { super(code.name()); this.code=code; }
    public Code code() { return code; }
}

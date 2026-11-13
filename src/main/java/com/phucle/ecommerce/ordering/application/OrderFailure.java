package com.phucle.ecommerce.ordering.application;

public final class OrderFailure extends RuntimeException {
    private final String code;
    public OrderFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}

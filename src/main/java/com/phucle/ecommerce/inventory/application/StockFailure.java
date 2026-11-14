package com.phucle.ecommerce.inventory.application;

public final class StockFailure extends RuntimeException {
    private final String code;
    public StockFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}

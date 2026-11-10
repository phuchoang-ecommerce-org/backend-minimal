package com.phucle.ecommerce.discovery;

public final class PublicProductNotFoundException extends RuntimeException {
    public PublicProductNotFoundException() { super("Public product not found"); }
}

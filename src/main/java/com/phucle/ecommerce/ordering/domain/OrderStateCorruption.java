package com.phucle.ecommerce.ordering.domain;

/** Persisted order facts violate aggregate invariants; never expose row details to clients. */
public final class OrderStateCorruption extends RuntimeException {
    public OrderStateCorruption(String reason) { super(reason); }
}

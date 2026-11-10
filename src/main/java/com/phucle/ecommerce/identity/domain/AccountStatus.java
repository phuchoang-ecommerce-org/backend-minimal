package com.phucle.ecommerce.identity.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AccountStatus(String value) {
    public static final AccountStatus ACTIVE = new AccountStatus("ACTIVE");

    public AccountStatus {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("status");
    }
}

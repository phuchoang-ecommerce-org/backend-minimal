package com.phucle.ecommerce.identity.domain;

import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AccountId(UUID value) {
    public AccountId { Objects.requireNonNull(value); }
}

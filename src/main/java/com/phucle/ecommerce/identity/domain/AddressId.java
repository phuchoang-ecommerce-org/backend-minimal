package com.phucle.ecommerce.identity.domain;

import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AddressId(UUID value) {
    public AddressId { Objects.requireNonNull(value); }
}

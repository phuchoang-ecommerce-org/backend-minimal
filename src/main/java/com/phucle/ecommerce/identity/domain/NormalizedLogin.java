package com.phucle.ecommerce.identity.domain;

import java.util.Locale;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record NormalizedLogin(String value) {
    public NormalizedLogin {
        Objects.requireNonNull(value);
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 100) throw new IllegalArgumentException("login");
    }
}

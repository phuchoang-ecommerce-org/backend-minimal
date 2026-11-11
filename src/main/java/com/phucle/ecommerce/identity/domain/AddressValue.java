package com.phucle.ecommerce.identity.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AddressValue(String recipient, String addressLine, String locality, String postalCode, String country) {
    public AddressValue {
        check(recipient, 100); check(addressLine, 200); check(locality, 100);
        check(postalCode, 100); check(country, 100);
    }
    private static void check(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException("Invalid address value");
    }
}

package com.phucle.ecommerce.ordering.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record AddressSnapshot(String recipient, String addressLine, String locality, String postalCode, String country) {
    public AddressSnapshot {
        if (blank(recipient) || blank(addressLine) || blank(locality) || blank(postalCode) || blank(country))
            throw new IllegalArgumentException("Invalid address snapshot");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}

package com.phucle.ecommerce.identity.api;

public record CreateAddressPayload(String recipient, String addressLine, String locality,
        String postalCode, String country) {}

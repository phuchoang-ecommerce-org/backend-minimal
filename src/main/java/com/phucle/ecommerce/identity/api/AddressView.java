package com.phucle.ecommerce.identity.api;

import java.util.UUID;

public record AddressView(UUID id, String recipient, String addressLine, String locality,
        String postalCode, String country) {}

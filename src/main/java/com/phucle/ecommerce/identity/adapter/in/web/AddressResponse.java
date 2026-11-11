package com.phucle.ecommerce.identity.adapter.in.web;

import java.util.UUID;

record AddressResponse(UUID id, String recipient, String addressLine, String locality, String postalCode, String country) {}

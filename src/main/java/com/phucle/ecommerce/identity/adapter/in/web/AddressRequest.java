package com.phucle.ecommerce.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AddressRequest(@NotBlank @Size(max = 100) String recipient,
        @NotBlank @Size(max = 200) String addressLine,
        @NotBlank @Size(max = 100) String locality,
        @NotBlank @Size(max = 100) String postalCode,
        @NotBlank @Size(max = 100) String country) {}

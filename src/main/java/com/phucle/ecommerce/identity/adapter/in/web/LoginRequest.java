package com.phucle.ecommerce.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotBlank @Size(max = 100) String login,
        @NotBlank @Size(max = 128) String password) {
}

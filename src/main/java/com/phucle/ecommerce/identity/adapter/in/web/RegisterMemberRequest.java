package com.phucle.ecommerce.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterMemberRequest(
        @NotBlank @Size(max = 100) String login,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 100) String displayName) {
}

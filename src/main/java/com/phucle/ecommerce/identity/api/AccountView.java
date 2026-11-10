package com.phucle.ecommerce.identity.api;

import java.util.UUID;

public record AccountView(UUID id, String login, String displayName, String role) {
}

package com.phucle.ecommerce.identity.adapter.in.web;

import java.util.UUID;

record AccountResponse(UUID id, String login, String displayName, String role) {
}

package com.phucle.ecommerce.identity.api;

public record RegisterMemberCommand(String login, String password, String displayName) {
}

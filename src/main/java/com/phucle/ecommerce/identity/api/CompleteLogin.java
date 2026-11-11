package com.phucle.ecommerce.identity.api;

public interface CompleteLogin {

    CompletedLogin complete(LoginCommand command, String guestProof, String origin);
}

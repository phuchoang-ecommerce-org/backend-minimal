package com.phucle.ecommerce.identity.application;

import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UUID accountId, String persistedRole);
}

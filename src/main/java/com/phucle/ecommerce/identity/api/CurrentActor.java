package com.phucle.ecommerce.identity.api;

import jakarta.servlet.http.HttpServletRequest;

public interface CurrentActor {
    TrustedActor require(HttpServletRequest request);
}

package com.phucle.ecommerce.identity.api;

import java.util.List;
import java.util.UUID;

public record CompletedLogin(LoginResult login, boolean merged, boolean sourceConsumed,
                             boolean secureCookie, List<RejectedLine> rejectedLines) {

    public record RejectedLine(UUID variantId, String code) {
    }
}

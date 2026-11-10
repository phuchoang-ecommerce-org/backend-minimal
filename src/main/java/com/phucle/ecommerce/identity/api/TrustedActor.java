package com.phucle.ecommerce.identity.api;

import java.util.UUID;

public record TrustedActor(UUID accountId, String operationalRole, String correlationId) {
}

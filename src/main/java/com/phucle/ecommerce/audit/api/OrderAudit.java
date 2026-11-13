package com.phucle.ecommerce.audit.api;

import java.util.UUID;

public interface OrderAudit {
    void placed(UUID actorId, UUID orderId);
    void cancelled(UUID actorId, UUID orderId);
}

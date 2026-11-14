package com.phucle.ecommerce.audit.api;

import java.util.UUID;
import java.time.Instant;

public interface OrderAudit {
    void placed(UUID actorId, UUID orderId);
    void cancelled(UUID actorId, UUID orderId);
    void sellerPaid(UUID actorId, UUID sellerOrderId, Instant paidAt);
}

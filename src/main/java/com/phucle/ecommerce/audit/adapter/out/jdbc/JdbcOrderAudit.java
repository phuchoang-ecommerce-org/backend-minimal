package com.phucle.ecommerce.audit.adapter.out.jdbc;

import com.phucle.ecommerce.audit.api.OrderAudit;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderAudit implements OrderAudit {
    private final JdbcTemplate jdbc;
    JdbcOrderAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void placed(UUID actorId, UUID orderId) {
        jdbc.update("INSERT INTO marketplace.audit_entry(id,actor_id,action,resource_type,resource_id,correlation_id) "
                + "VALUES (?,?,'ORDER_PLACED','CUSTOMER_ORDER',?,?)", UUID.randomUUID(), actorId, orderId, UUID.randomUUID());
    }
    @Override public void cancelled(UUID actorId, UUID orderId) {
        jdbc.update("INSERT INTO marketplace.audit_entry(id,actor_id,action,resource_type,resource_id,correlation_id) "
                + "VALUES (?,?,'ORDER_CANCELLED','CUSTOMER_ORDER',?,?)", UUID.randomUUID(), actorId, orderId, UUID.randomUUID());
    }
    @Override public void sellerPaid(UUID actorId, UUID sellerOrderId, Instant paidAt) {
        jdbc.update("INSERT INTO marketplace.audit_entry(id,actor_id,action,resource_type,resource_id,correlation_id,created_at) "
                + "VALUES (?,?,'SELLER_ORDER_PAID','SELLER_ORDER',?,?,?)", UUID.randomUUID(), actorId,
                sellerOrderId, UUID.randomUUID(), Timestamp.from(paidAt));
    }
}

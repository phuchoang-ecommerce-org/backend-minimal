package com.phucle.ecommerce.audit.adapter.out.jdbc;

import com.phucle.ecommerce.audit.api.OrderAudit;
import java.util.UUID;
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
}

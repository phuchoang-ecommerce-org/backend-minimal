package com.phucle.ecommerce.ordering.adapter.out.jdbc;

import com.phucle.ecommerce.ordering.application.OrderWritePort;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import com.phucle.ecommerce.ordering.domain.SellerOrder;
import com.phucle.ecommerce.ordering.domain.OrderLine;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderWrite implements OrderWritePort {
    private final JdbcTemplate jdbc;
    JdbcOrderWrite(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void serializeIntent(UUID memberId, String key) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, memberId + ":" + key);
    }
    @Override public Optional<Existing> findExisting(UUID memberId, String key) {
        return jdbc.query("SELECT id,request_fingerprint FROM marketplace.customer_order WHERE member_id=? AND idempotency_key=?",
                (rs, row) -> new Existing(rs.getObject(1, UUID.class), rs.getString(2)), memberId, key).stream().findFirst();
    }
    @Override public void save(String key, String fingerprint, CustomerOrder order) {
        jdbc.update("INSERT INTO marketplace.customer_order(id,member_id,idempotency_key,request_fingerprint,status,total_usd,currency) "
                + "VALUES (?,?,?,?,?,?,'USD')", order.id(), order.memberId(), key, fingerprint, order.status(), order.totalUsd());
        var address = order.address();
        jdbc.update("INSERT INTO marketplace.order_address_snapshot(customer_order_id,recipient,address_line,locality,postal_code,country) "
                + "VALUES (?,?,?,?,?,?)", order.id(), address.recipient(), address.addressLine(), address.locality(),
                address.postalCode(), address.country());
        for (SellerOrder portion : order.portions()) {
            jdbc.update("INSERT INTO marketplace.seller_order(id,customer_order_id,seller_id,payment_status,total_usd) "
                    + "VALUES (?,?,?,?,?)", portion.id(), order.id(), portion.sellerId(), portion.paymentStatus(), portion.totalUsd());
            for (OrderLine purchased : portion.lines()) {
                var line = purchased.snapshot();
                jdbc.update("INSERT INTO marketplace.order_line(id,seller_order_id,variant_id,product_id,product_title_snapshot,"
                        + "sku_snapshot,quantity,unit_price_usd,discount_usd,line_total_usd) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        purchased.id(), portion.id(), line.variantId(), line.productId(), line.productTitle(), line.sku(),
                        line.quantity(), line.unitPrice().amount(), line.discount().amount(), line.total().amount());
            }
        }
    }
}

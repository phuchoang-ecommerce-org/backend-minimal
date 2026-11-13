package com.phucle.ecommerce.ordering.adapter.out.jdbc;

import com.phucle.ecommerce.ordering.application.OrderWritePort;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import com.phucle.ecommerce.ordering.domain.SellerOrder;
import com.phucle.ecommerce.ordering.domain.OrderLine;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import com.phucle.ecommerce.ordering.domain.PurchasedLineSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
    @Override public Optional<CustomerOrder> lockOwned(UUID memberId, UUID orderId) {
        var roots = jdbc.query("SELECT id,status,cancelled_at,total_usd FROM marketplace.customer_order "
                        + "WHERE id=? AND member_id=? FOR UPDATE",
                (rs, row) -> new LockedRoot(rs.getObject("id", UUID.class), rs.getString("status"),
                        instant(rs.getTimestamp("cancelled_at")), rs.getBigDecimal("total_usd")), orderId, memberId);
        if (roots.isEmpty()) return Optional.empty();
        LockedRoot root = roots.getFirst();
        AddressSnapshot address = jdbc.queryForObject("SELECT recipient,address_line,locality,postal_code,country "
                        + "FROM marketplace.order_address_snapshot WHERE customer_order_id=?",
                (rs, row) -> new AddressSnapshot(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)), orderId);
        List<SellerOrder> portions = new ArrayList<>();
        var rows = jdbc.query("SELECT id,seller_id,payment_status,paid_at,total_usd FROM marketplace.seller_order "
                        + "WHERE customer_order_id=? ORDER BY id",
                (rs, row) -> new PortionRow(rs.getObject("id", UUID.class), rs.getObject("seller_id", UUID.class),
                        rs.getString("payment_status"), instant(rs.getTimestamp("paid_at")),
                        rs.getBigDecimal("total_usd")), orderId);
        for (PortionRow portion : rows) {
            List<OrderLine> lines = jdbc.query("SELECT id,product_id,variant_id,product_title_snapshot,sku_snapshot,"
                            + "quantity,unit_price_usd,discount_usd,line_total_usd FROM marketplace.order_line WHERE seller_order_id=? ORDER BY id",
                    (rs, row) -> OrderLine.rehydrate(rs.getObject("id", UUID.class), PurchasedLineSnapshot.priced(
                            rs.getObject("product_id", UUID.class), portion.sellerId(), rs.getObject("variant_id", UUID.class),
                            rs.getString("product_title_snapshot"), rs.getString("sku_snapshot"), rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price_usd"), rs.getBigDecimal("discount_usd")),
                            rs.getBigDecimal("line_total_usd")), portion.id());
            portions.add(SellerOrder.rehydrate(portion.id(), portion.sellerId(), lines, portion.paymentStatus(),
                    portion.paidAt(), portion.totalUsd()));
        }
        return Optional.of(CustomerOrder.rehydrate(root.id(), memberId, address, portions, root.status(),
                root.cancelledAt(), root.totalUsd()));
    }
    @Override public void saveCancellation(CustomerOrder order) {
        if (jdbc.update("UPDATE marketplace.customer_order SET status='CANCELLED',cancelled_at=?,version=version+1 "
                        + "WHERE id=? AND member_id=? AND status='PLACED'",
                Timestamp.from(order.cancelledAt()), order.id(), order.memberId()) != 1)
            throw new IllegalStateException("Order cancellation state changed unexpectedly");
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private record LockedRoot(UUID id, String status, Instant cancelledAt, BigDecimal totalUsd) {}
    private record PortionRow(UUID id, UUID sellerId, String paymentStatus, Instant paidAt, BigDecimal totalUsd) {}
}

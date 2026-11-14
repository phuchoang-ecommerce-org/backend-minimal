package com.phucle.ecommerce.ordering.adapter.out.jdbc;

import com.phucle.ecommerce.ordering.application.SellerOrderReadPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSellerOrderRead implements SellerOrderReadPort {
    private final JdbcTemplate jdbc;
    JdbcSellerOrderRead(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<Boundary> pageOwned(UUID sellerId, Instant beforeTime, UUID beforeId, int fetchLimit) {
        String base = "SELECT so.id,co.created_at FROM marketplace.seller_order so "
                + "JOIN marketplace.customer_order co ON co.id=so.customer_order_id WHERE so.seller_id=? ";
        if (beforeTime == null) {
            return jdbc.query(base + "ORDER BY co.created_at DESC,so.id DESC LIMIT ?",
                    (rs, row) -> new Boundary(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                    sellerId, fetchLimit);
        }
        return jdbc.query(base + "AND (co.created_at,so.id)<(?,?) ORDER BY co.created_at DESC,so.id DESC LIMIT ?",
                (rs, row) -> new Boundary(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                sellerId, Timestamp.from(beforeTime), beforeId, fetchLimit);
    }

    @Override public List<Portion> findOwnedAll(UUID sellerId, List<UUID> portionIds) {
        if (portionIds.isEmpty()) return List.of();
        List<PortionRow> portions = readPortions(sellerId, portionIds);
        if (portions.isEmpty()) return List.of();
        Map<UUID, List<Line>> lines = readLines(sellerId, portions.stream().map(PortionRow::id).toList());
        Map<UUID, Portion> byId = new HashMap<>();
        portions.forEach(p -> byId.put(p.id(), new Portion(p.id(), sellerId, p.paymentStatus(), p.totalUsd(),
                p.createdAt(), p.orderId(), p.status(), lines.getOrDefault(p.id(), List.of()), p.paidAt())));
        return portionIds.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    private List<PortionRow> readPortions(UUID sellerId, List<UUID> ids) {
        Object[] args = args(sellerId, ids);
        return jdbc.query("SELECT so.id,so.payment_status,so.total_usd,so.paid_at,co.created_at,co.id,co.status "
                        + "FROM marketplace.seller_order so JOIN marketplace.customer_order co ON co.id=so.customer_order_id "
                        + "WHERE so.seller_id=? AND so.id IN (" + placeholders(ids.size()) + ")",
                (rs, row) -> new PortionRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
                        rs.getTimestamp(5).toInstant(), rs.getObject(6, UUID.class), rs.getString(7)), args);
    }

    private Map<UUID, List<Line>> readLines(UUID sellerId, List<UUID> ids) {
        Map<UUID, List<Line>> lines = new HashMap<>();
        jdbc.query("SELECT l.seller_order_id,l.product_id,l.variant_id,l.product_title_snapshot,l.sku_snapshot,"
                        + "l.quantity,l.unit_price_usd,l.discount_usd,l.line_total_usd FROM marketplace.order_line l "
                        + "JOIN marketplace.seller_order so ON so.id=l.seller_order_id "
                        + "WHERE so.seller_id=? AND l.seller_order_id IN (" + placeholders(ids.size()) + ") "
                        + "ORDER BY l.seller_order_id,l.id",
                (rs, row) -> new LineRow(rs.getObject(1, UUID.class), new Line(rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getInt(6),
                        rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9))), args(sellerId, ids))
                .forEach(row -> lines.computeIfAbsent(row.portionId(), ignored -> new ArrayList<>()).add(row.line()));
        return lines;
    }
    private static Object[] args(UUID sellerId, List<UUID> ids) {
        Object[] args = new Object[ids.size() + 1];
        args[0] = sellerId;
        for (int index = 0; index < ids.size(); index++) args[index + 1] = ids.get(index);
        return args;
    }
    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private record PortionRow(UUID id, String paymentStatus, BigDecimal totalUsd, Instant paidAt,
                              Instant createdAt, UUID orderId, String status) {}
    private record LineRow(UUID portionId, Line line) {}
}

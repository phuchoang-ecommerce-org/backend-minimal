package com.phucle.ecommerce.ordering.adapter.out.jdbc;

import com.phucle.ecommerce.ordering.application.OwnOrderReadPort;
import com.phucle.ecommerce.ordering.application.CancelOrderResultReadPort;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOwnOrderRead implements OwnOrderReadPort, CancelOrderResultReadPort {
    private final JdbcTemplate jdbc;
    JdbcOwnOrderRead(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Order> findOwned(UUID memberId, UUID orderId) {
        return findOwnedAll(memberId, List.of(orderId)).stream().findFirst();
    }

    @Override public List<Order> findOwnedAll(UUID memberId, List<UUID> orderIds) {
        if (orderIds.isEmpty()) return List.of();
        List<Root> roots = jdbc.query("SELECT id,member_id,status,total_usd,created_at FROM marketplace.customer_order "
                + "WHERE member_id=? AND id IN (" + placeholders(orderIds.size()) + ")",
                (rs, row) -> new Root(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getBigDecimal(4), rs.getTimestamp(5).toInstant()), memberArgs(memberId, orderIds));
        if (roots.isEmpty()) return List.of();
        Map<UUID, Root> rootsById = new HashMap<>();
        roots.forEach(root -> rootsById.put(root.id(), root));
        List<UUID> ownedIds = roots.stream().map(Root::id).toList();
        String ownedPlaceholders = placeholders(ownedIds.size());
        Map<UUID, AddressSnapshot> addresses = new HashMap<>();
        jdbc.query("SELECT customer_order_id,recipient,address_line,locality,postal_code,country "
                + "FROM marketplace.order_address_snapshot WHERE customer_order_id IN (" + ownedPlaceholders + ")",
                (rs, row) -> new AddressRow(rs.getObject(1, UUID.class), new AddressSnapshot(rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6))), ownedIds.toArray())
                .forEach(row -> addresses.put(row.orderId(), row.address()));
        List<Portion> portions = jdbc.query("SELECT id,customer_order_id,seller_id,payment_status,total_usd,paid_at "
                + "FROM marketplace.seller_order WHERE customer_order_id IN (" + ownedPlaceholders + ") ORDER BY customer_order_id,id",
                (rs, row) -> new Portion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getBigDecimal(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), ownedIds.toArray());
        Map<UUID, List<PurchasedLine>> linesByPortion = new HashMap<>();
        if (!portions.isEmpty()) {
            List<UUID> portionIds = portions.stream().map(Portion::id).toList();
            jdbc.query("SELECT seller_order_id,product_id,variant_id,product_title_snapshot,sku_snapshot,"
                    + "quantity,unit_price_usd,discount_usd,line_total_usd FROM marketplace.order_line "
                    + "WHERE seller_order_id IN (" + placeholders(portionIds.size()) + ") ORDER BY seller_order_id,id",
                    (rs, row) -> new LineRow(rs.getObject(1, UUID.class), new PurchasedLine(rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getInt(6),
                            rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9))), portionIds.toArray())
                    .forEach(row -> linesByPortion.computeIfAbsent(row.portionId(), ignored -> new ArrayList<>()).add(row.line()));
        }
        Map<UUID, List<Portion>> portionsByOrder = new HashMap<>();
        portions.forEach(portion -> portionsByOrder.computeIfAbsent(portion.orderId(), ignored -> new ArrayList<>()).add(portion));
        List<Order> result = new ArrayList<>();
        for (UUID orderId : orderIds) {
            Root root = rootsById.get(orderId);
            if (root == null) continue;
            List<Portion> ownedPortions = portionsByOrder.getOrDefault(orderId, List.of());
            List<SellerOrder> sellerOrders = new ArrayList<>();
            BigDecimal discount = new BigDecimal("0.00");
            for (Portion portion : ownedPortions) {
                List<PurchasedLine> lines = List.copyOf(linesByPortion.getOrDefault(portion.id(), List.of()));
                for (PurchasedLine line : lines) discount = discount.add(line.discountUsd());
                sellerOrders.add(new SellerOrder(portion.id(), portion.sellerId(), portion.paymentStatus(),
                        portion.totalUsd(), root.createdAt(), orderId, root.status(), lines, portion.paidAt()));
            }
            String paymentStatus = !ownedPortions.isEmpty()
                    && ownedPortions.stream().allMatch(portion -> "PAID".equals(portion.paymentStatus()))
                    ? "PAID" : "PENDING_PAYMENT";
            result.add(new Order(root.id(), root.memberId(), root.status(), paymentStatus, root.totalUsd(),
                    List.copyOf(sellerOrders), root.createdAt(), addresses.get(orderId), discount, "COD", null));
        }
        return List.copyOf(result);
    }

    @Override public List<Boundary> pageOwned(UUID memberId, Instant beforeTime, UUID beforeId, int fetchLimit) {
        if (beforeTime == null) {
            return jdbc.query("SELECT id,created_at FROM marketplace.customer_order WHERE member_id=? "
                    + "ORDER BY created_at DESC,id DESC LIMIT ?", (rs, row) -> new Boundary(rs.getObject(1, UUID.class),
                    rs.getTimestamp(2).toInstant()), memberId, fetchLimit);
        }
        return jdbc.query("SELECT id,created_at FROM marketplace.customer_order WHERE member_id=? "
                + "AND (created_at,id)<(?,?) ORDER BY created_at DESC,id DESC LIMIT ?",
                (rs, row) -> new Boundary(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()),
                memberId, java.sql.Timestamp.from(beforeTime), beforeId, fetchLimit);
    }

    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private static Object[] memberArgs(UUID memberId, List<UUID> ids) {
        Object[] args = new Object[ids.size() + 1];
        args[0] = memberId;
        for (int index = 0; index < ids.size(); index++) args[index + 1] = ids.get(index);
        return args;
    }
    private record Root(UUID id, UUID memberId, String status, BigDecimal totalUsd, Instant createdAt) {}
    private record AddressRow(UUID orderId, AddressSnapshot address) {}
    private record Portion(UUID id, UUID orderId, UUID sellerId, String paymentStatus, BigDecimal totalUsd, Instant paidAt) {}
    private record LineRow(UUID portionId, PurchasedLine line) {}
}

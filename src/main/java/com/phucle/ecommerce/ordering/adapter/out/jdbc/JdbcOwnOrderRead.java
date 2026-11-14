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
        List<Root> roots = readRoots(memberId, orderIds);
        if (roots.isEmpty()) return List.of();
        List<UUID> ownedIds = roots.stream().map(Root::id).toList();
        Map<UUID, AddressSnapshot> addresses = readAddresses(ownedIds);
        List<Portion> portions = readPortions(ownedIds);
        Map<UUID, List<PurchasedLine>> lines = readLines(portions);
        return assembleOrders(orderIds, roots, addresses, portions, lines);
    }

    private List<Root> readRoots(UUID memberId, List<UUID> orderIds) {
        return jdbc.query("SELECT id,member_id,status,total_usd,created_at FROM marketplace.customer_order "
                + "WHERE member_id=? AND id IN (" + placeholders(orderIds.size()) + ")",
                (rs, row) -> new Root(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getBigDecimal(4), rs.getTimestamp(5).toInstant()), memberArgs(memberId, orderIds));
    }

    private Map<UUID, AddressSnapshot> readAddresses(List<UUID> ownedIds) {
        Map<UUID, AddressSnapshot> addresses = new HashMap<>();
        jdbc.query("SELECT customer_order_id,recipient,address_line,locality,postal_code,country "
                + "FROM marketplace.order_address_snapshot WHERE customer_order_id IN (" + placeholders(ownedIds.size()) + ")",
                (rs, row) -> new AddressRow(rs.getObject(1, UUID.class), new AddressSnapshot(rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6))), ownedIds.toArray())
                .forEach(row -> addresses.put(row.orderId(), row.address()));
        return addresses;
    }

    private List<Portion> readPortions(List<UUID> ownedIds) {
        return jdbc.query("SELECT id,customer_order_id,seller_id,payment_status,total_usd,paid_at "
                + "FROM marketplace.seller_order WHERE customer_order_id IN (" + placeholders(ownedIds.size()) + ") ORDER BY customer_order_id,id",
                (rs, row) -> new Portion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getBigDecimal(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), ownedIds.toArray());
    }

    private Map<UUID, List<PurchasedLine>> readLines(List<Portion> portions) {
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
        return linesByPortion;
    }

    private List<Order> assembleOrders(List<UUID> orderIds, List<Root> roots,
            Map<UUID, AddressSnapshot> addresses, List<Portion> portions,
            Map<UUID, List<PurchasedLine>> linesByPortion) {
        Map<UUID, Root> rootsById = new HashMap<>();
        roots.forEach(root -> rootsById.put(root.id(), root));
        Map<UUID, List<Portion>> portionsByOrder = new HashMap<>();
        portions.forEach(portion -> portionsByOrder.computeIfAbsent(portion.orderId(), ignored -> new ArrayList<>()).add(portion));
        List<Order> result = new ArrayList<>();
        for (UUID orderId : orderIds) {
            Root root = rootsById.get(orderId);
            if (root == null) continue;
            List<Portion> ownedPortions = portionsByOrder.getOrDefault(orderId, List.of());
            result.add(toOrder(root, ownedPortions, linesByPortion, addresses.get(orderId)));
        }
        return List.copyOf(result);
    }

    private Order toOrder(Root root, List<Portion> portions,
            Map<UUID, List<PurchasedLine>> linesByPortion, AddressSnapshot address) {
        List<SellerOrder> sellerOrders = toSellerOrders(root, portions, linesByPortion);
        return new Order(root.id(), root.memberId(), root.status(), paymentStatus(portions), root.totalUsd(),
                List.copyOf(sellerOrders), root.createdAt(), address, discount(sellerOrders), "COD", null);
    }

    private List<SellerOrder> toSellerOrders(Root root, List<Portion> portions,
            Map<UUID, List<PurchasedLine>> linesByPortion) {
        return portions.stream().map(portion -> new SellerOrder(portion.id(),
                portion.sellerId(), portion.paymentStatus(), portion.totalUsd(), root.createdAt(), root.id(),
                root.status(), List.copyOf(linesByPortion.getOrDefault(portion.id(), List.of())), portion.paidAt())).toList();
    }

    private BigDecimal discount(List<SellerOrder> portions) {
        return portions.stream().flatMap(portion -> portion.lines().stream())
                .map(PurchasedLine::discountUsd).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    private String paymentStatus(List<Portion> portions) {
        return !portions.isEmpty()
                && portions.stream().allMatch(portion -> "PAID".equals(portion.paymentStatus()))
                ? "PAID" : "PENDING_PAYMENT";
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

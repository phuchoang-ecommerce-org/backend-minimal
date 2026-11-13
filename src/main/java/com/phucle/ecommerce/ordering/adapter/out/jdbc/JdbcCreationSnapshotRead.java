package com.phucle.ecommerce.ordering.adapter.out.jdbc;

import com.phucle.ecommerce.ordering.application.CreationSnapshotReadPort;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCreationSnapshotRead implements CreationSnapshotReadPort {
    private final JdbcTemplate jdbc;
    JdbcCreationSnapshotRead(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Order readCreation(UUID orderId, String voucherCode) {
        var order = jdbc.queryForObject("SELECT member_id,total_usd,created_at FROM marketplace.customer_order WHERE id=?",
                (rs, row) -> new Root(rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getTimestamp(3).toInstant()), orderId);
        var address = jdbc.queryForObject("SELECT recipient,address_line,locality,postal_code,country "
                + "FROM marketplace.order_address_snapshot WHERE customer_order_id=?",
                (rs, row) -> new AddressSnapshot(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)), orderId);
        var portions = jdbc.query("SELECT id,seller_id,total_usd FROM marketplace.seller_order WHERE customer_order_id=? ORDER BY id",
                (rs, row) -> new Portion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getBigDecimal(3)), orderId);
        List<SellerOrder> sellerOrders = new ArrayList<>();
        BigDecimal discount = new BigDecimal("0.00");
        for (Portion portion : portions) {
            List<PurchasedLine> purchased = jdbc.query("SELECT product_id,variant_id,product_title_snapshot,sku_snapshot,"
                    + "quantity,unit_price_usd,discount_usd,line_total_usd FROM marketplace.order_line "
                    + "WHERE seller_order_id=? ORDER BY id", (rs, row) -> new PurchasedLine(rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getInt(5),
                            rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)), portion.id());
            for (PurchasedLine line : purchased) discount = discount.add(line.discountUsd());
            sellerOrders.add(new SellerOrder(portion.id(), portion.sellerId(), "PENDING_PAYMENT", portion.total(),
                    order.createdAt(), orderId, "PLACED", purchased, null));
        }
        return new Order(orderId, order.memberId(), "PLACED", "PENDING_PAYMENT", order.total(), sellerOrders,
                order.createdAt(), address, discount, "COD", voucherCode);
    }
    private record Root(UUID memberId, BigDecimal total, Instant createdAt) {}
    private record Portion(UUID id, UUID sellerId, BigDecimal total) {}
}

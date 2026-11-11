package com.phucle.ecommerce.cart.adapter.out.jdbc;

import com.phucle.ecommerce.cart.application.port.out.CartReadPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCartReadAdapter implements CartReadPort {
    private final JdbcTemplate jdbc;
    JdbcCartReadAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Snapshot member(UUID owner) {
        return find("member_id=?", owner);
    }
    @Override public Snapshot guest(String sessionHash) {
        if (sessionHash == null) return null;
        return find("guest_session_hash=? AND expires_at>now()", sessionHash);
    }
    private Snapshot find(String predicate, Object owner) {
        return jdbc.query("""
                SELECT c.version, c.merged_into_cart_id, l.variant_id, l.quantity
                FROM marketplace.cart c LEFT JOIN marketplace.cart_line l ON l.cart_id=c.id
                WHERE c.""" + predicate + " ORDER BY l.variant_id",
                rs -> {
                    if (!rs.next()) return null;
                    long version = rs.getLong(1);
                    boolean consumed = rs.getObject(2) != null;
                    var lines = new java.util.ArrayList<Line>();
                    do {
                        UUID variant = rs.getObject(3, UUID.class);
                        if (variant != null) lines.add(new Line(variant, rs.getInt(4)));
                    } while (rs.next());
                    return new Snapshot(version, consumed, lines);
                }, owner);
    }
}

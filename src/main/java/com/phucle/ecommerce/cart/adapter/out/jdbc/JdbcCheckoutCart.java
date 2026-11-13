package com.phucle.ecommerce.cart.adapter.out.jdbc;

import com.phucle.ecommerce.cart.api.CheckoutCart;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCheckoutCart implements CheckoutCart {
    private final JdbcTemplate jdbc;
    JdbcCheckoutCart(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Locked lockOwned(UUID memberId) {
        var carts = jdbc.query("SELECT id,version FROM marketplace.cart WHERE member_id=? FOR UPDATE",
                (rs, row) -> new Locked(rs.getObject(1, UUID.class), rs.getLong(2),
                        jdbc.query("SELECT variant_id,quantity FROM marketplace.cart_line WHERE cart_id=? ORDER BY variant_id",
                                (line, n) -> new Line(line.getObject(1, UUID.class), line.getInt(2)),
                                rs.getObject(1, UUID.class))), memberId);
        return carts.isEmpty() ? null : carts.getFirst();
    }
    @Override public void consume(UUID cartId, long expectedVersion) {
        jdbc.update("DELETE FROM marketplace.cart_line WHERE cart_id=?", cartId);
        if (jdbc.update("UPDATE marketplace.cart SET version=version+1 WHERE id=? AND version=?", cartId, expectedVersion) != 1)
            throw new IllegalStateException("Locked cart version changed");
    }
}

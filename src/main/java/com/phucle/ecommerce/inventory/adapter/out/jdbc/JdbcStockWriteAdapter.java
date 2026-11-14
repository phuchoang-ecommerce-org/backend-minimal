package com.phucle.ecommerce.inventory.adapter.out.jdbc;

import com.phucle.ecommerce.inventory.application.StockWritePort;
import com.phucle.ecommerce.inventory.domain.StockItem;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockWriteAdapter implements StockWritePort {
    private final JdbcTemplate jdbc;
    JdbcStockWriteAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public StockItem find(UUID variantId) {
        return jdbc.query("SELECT variant_id,on_hand,version FROM marketplace.stock WHERE variant_id=?",
                (r,n) -> new StockItem(r.getObject("variant_id", UUID.class), r.getInt("on_hand"), r.getLong("version")),
                variantId).stream().findFirst().orElse(null);
    }
    @Override public StockItem replace(StockItem previous, StockItem updated) {
        if (!previous.variantId().equals(updated.variantId()) || updated.version() != previous.version() + 1)
            throw new IllegalArgumentException("Invalid stock transition");
        return jdbc.query("""
                UPDATE marketplace.stock SET on_hand=?,version=?
                WHERE variant_id=? AND version=?
                RETURNING variant_id,on_hand,version
                """, (r,n) -> new StockItem(r.getObject("variant_id", UUID.class), r.getInt("on_hand"), r.getLong("version")),
                updated.onHand(), updated.version(), previous.variantId(), previous.version()).stream().findFirst().orElse(null);
    }
}

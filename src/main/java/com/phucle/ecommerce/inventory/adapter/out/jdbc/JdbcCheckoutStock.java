package com.phucle.ecommerce.inventory.adapter.out.jdbc;

import com.phucle.ecommerce.inventory.api.CheckoutStock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCheckoutStock implements CheckoutStock {
    private final JdbcTemplate jdbc;
    JdbcCheckoutStock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean decrease(Map<UUID, Integer> quantities) {
        for (UUID variantId : quantities.keySet().stream().sorted().toList()) {
            int amount = quantities.get(variantId);
            if (jdbc.update("UPDATE marketplace.stock SET on_hand=on_hand-?,version=version+1 "
                    + "WHERE variant_id=? AND on_hand>=?", amount, variantId, amount) != 1) return false;
        }
        return true;
    }
}

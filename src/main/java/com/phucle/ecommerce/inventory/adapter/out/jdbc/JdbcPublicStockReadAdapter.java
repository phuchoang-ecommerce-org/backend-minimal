package com.phucle.ecommerce.inventory.adapter.out.jdbc;

import com.phucle.ecommerce.inventory.api.PublicStockReadPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPublicStockReadAdapter implements PublicStockReadPort {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcPublicStockReadAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Map<UUID, Stock> findStock(Set<UUID> variantIds) {
        if (variantIds.isEmpty()) return Map.of();
        Map<UUID, Stock> found = new HashMap<>();
        jdbc.query("SELECT variant_id,on_hand,version FROM marketplace.stock WHERE variant_id IN (:ids)",
                new MapSqlParameterSource("ids", variantIds), r -> {
                    UUID id = r.getObject("variant_id", UUID.class);
                    found.put(id, new Stock(id, r.getInt("on_hand"), r.getLong("version")));
                });
        return Map.copyOf(found);
    }
}

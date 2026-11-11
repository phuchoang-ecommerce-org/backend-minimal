package com.phucle.ecommerce.catalog.adapter.out.jdbc;

import com.phucle.ecommerce.catalog.api.CartOfferReadPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCartOfferReadAdapter implements CartOfferReadPort {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcCartOfferReadAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Map<UUID, Offer> offers(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, Offer> result = new HashMap<>();
        jdbc.query("""
                SELECT v.id,v.price_usd,(p.published AND s.status='ACTIVE') AS purchasable
                FROM marketplace.variant v JOIN marketplace.product p ON p.id=v.product_id
                JOIN marketplace.seller_shop s ON s.id=p.seller_id
                WHERE v.id IN (:ids)
                """, new MapSqlParameterSource("ids", ids), r -> {
                    UUID id = r.getObject(1, UUID.class);
                    result.put(id, new Offer(id, r.getBigDecimal(2), r.getBoolean(3)));
                });
        return Map.copyOf(result);
    }
}

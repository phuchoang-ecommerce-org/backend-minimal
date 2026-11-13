package com.phucle.ecommerce.catalog.adapter.out.jdbc;

import com.phucle.ecommerce.catalog.api.CheckoutOffers;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCheckoutOffers implements CheckoutOffers {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcCheckoutOffers(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<Offer> lockCurrent(List<UUID> variantIds) {
        if (variantIds.isEmpty()) return List.of();
        return jdbc.query("SELECT p.id,p.seller_id,v.id,p.title,v.sku,v.price_usd,p.published,s.status "
                + "FROM marketplace.variant v JOIN marketplace.product p ON p.id=v.product_id "
                + "JOIN marketplace.seller_shop s ON s.id=p.seller_id WHERE v.id IN (:ids) "
                + "ORDER BY v.id FOR UPDATE OF p,v,s", new MapSqlParameterSource("ids", variantIds),
                (rs, row) -> new Offer(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
                        rs.getBoolean(7) && "ACTIVE".equals(rs.getString(8))));
    }
}

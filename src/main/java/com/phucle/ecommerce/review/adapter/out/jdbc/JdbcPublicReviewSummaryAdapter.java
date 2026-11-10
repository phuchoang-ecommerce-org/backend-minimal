package com.phucle.ecommerce.review.adapter.out.jdbc;

import com.phucle.ecommerce.review.api.PublicReviewSummaryPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPublicReviewSummaryAdapter implements PublicReviewSummaryPort {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcPublicReviewSummaryAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Map<UUID, Summary> summaries(Set<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<UUID, Summary> found = new HashMap<>();
        jdbc.query("""
                SELECT product_id,AVG(rating) rating_average,COUNT(*) review_count
                FROM marketplace.review WHERE product_id IN (:ids) AND visible AND removed_at IS NULL
                GROUP BY product_id
                """, new MapSqlParameterSource("ids", productIds), (org.springframework.jdbc.core.RowCallbackHandler) r -> found.put(
                r.getObject("product_id", UUID.class), new Summary(r.getBigDecimal("rating_average"), r.getInt("review_count"))));
        return Map.copyOf(found);
    }
}

package com.phucle.ecommerce.catalog.adapter.out.jdbc;

import com.phucle.ecommerce.catalog.api.PublicCatalogReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPublicCatalogReadAdapter implements PublicCatalogReadPort {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcPublicCatalogReadAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<Category> categoriesBefore(UUID boundary, int fetchLimit) {
        return jdbc.query("""
                SELECT id,name,version FROM marketplace.category
                WHERE (:boundary::uuid IS NULL OR id < :boundary::uuid)
                ORDER BY id DESC LIMIT :fetch
                """, new MapSqlParameterSource().addValue("boundary", boundary).addValue("fetch", fetchLimit),
                (r,n) -> new Category(r.getObject("id", UUID.class), r.getString("name"), r.getLong("version")));
    }

    @Override public List<Product> productsBefore(Filters filters, UUID boundary, int fetchLimit) {
        var args = new MapSqlParameterSource().addValue("boundary", boundary).addValue("fetch", fetchLimit)
                .addValue("q", filters.q()).addValue("category", filters.categoryId())
                .addValue("min", filters.minPrice()).addValue("max", filters.maxPrice());
        // Documented immutable public-offer projection: this Catalog adapter joins Seller status
        // so publication and activation are checked in the same PostgreSQL statement.
        List<Core> rows = jdbc.query("""
                SELECT p.id,p.seller_id,p.category_id,p.title,p.description,p.published,p.version
                FROM marketplace.product p JOIN marketplace.seller_shop s ON s.id=p.seller_id
                WHERE p.published AND s.status='ACTIVE'
                  AND (:boundary::uuid IS NULL OR p.id < :boundary::uuid)
                  AND (:category::uuid IS NULL OR p.category_id=:category::uuid)
                  AND (:q::text IS NULL OR p.search_document @@ websearch_to_tsquery('simple',:q))
                  AND EXISTS (SELECT 1 FROM marketplace.variant v WHERE v.product_id=p.id
                    AND (:min::numeric IS NULL OR v.price_usd >= :min::numeric)
                    AND (:max::numeric IS NULL OR v.price_usd <= :max::numeric))
                ORDER BY p.id DESC LIMIT :fetch
                """, args, (r,n) -> core(r));
        return enrich(rows);
    }

    @Override public Product product(UUID id) {
        List<Core> rows = jdbc.query("""
                SELECT p.id,p.seller_id,p.category_id,p.title,p.description,p.published,p.version
                FROM marketplace.product p JOIN marketplace.seller_shop s ON s.id=p.seller_id
                WHERE p.id=:id AND p.published AND s.status='ACTIVE'
                """, new MapSqlParameterSource("id", id), (r,n) -> core(r));
        return rows.isEmpty() ? null : enrich(rows).getFirst();
    }

    private Core core(ResultSet r) throws SQLException {
        return new Core(r.getObject("id", UUID.class), r.getObject("seller_id", UUID.class),
                r.getObject("category_id", UUID.class), r.getString("title"), r.getString("description"),
                r.getBoolean("published"), r.getLong("version"));
    }
    private List<Product> enrich(List<Core> cores) {
        if (cores.isEmpty()) return List.of();
        List<UUID> ids = cores.stream().map(Core::id).toList();
        Map<UUID, List<Variant>> variants = loadVariants(ids);
        Map<UUID, List<Image>> images = loadImages(cores, ids);
        return cores.stream().map(core -> new Product(core.id(), core.sellerId(), core.categoryId(), core.name(),
                core.description(), core.published(), core.version(), variants.getOrDefault(core.id(), List.of()),
                images.getOrDefault(core.id(), List.of()))).toList();
    }

    private Map<UUID, List<Variant>> loadVariants(List<UUID> ids) {
        Map<UUID, List<Variant>> variants = new HashMap<>();
        jdbc.query("""
                SELECT id,product_id,sku,label,price_usd FROM marketplace.variant
                WHERE product_id IN (:ids) ORDER BY product_id,id DESC
                """, new MapSqlParameterSource("ids", ids), (org.springframework.jdbc.core.RowCallbackHandler) r -> variants.computeIfAbsent(
                r.getObject("product_id", UUID.class), ignored -> new ArrayList<>()).add(new Variant(
                r.getObject("id", UUID.class), r.getString("sku"), r.getString("label"), r.getBigDecimal("price_usd"))));
        return variants;
    }

    private Map<UUID, List<Image>> loadImages(List<Core> cores, List<UUID> ids) {
        Map<UUID, List<Image>> images = new HashMap<>();
        Map<UUID, Long> versions = new HashMap<>();
        cores.forEach(c -> versions.put(c.id(), c.version()));
        jdbc.query("""
                SELECT id,product_id,mode,source_url,source_name,license,mime_type,byte_size,width,height,sort_order,alt_text
                FROM marketplace.product_image WHERE product_id IN (:ids) ORDER BY product_id,sort_order,id
                """, new MapSqlParameterSource("ids", ids), r -> {
            UUID productId = r.getObject("product_id", UUID.class);
            List<Image> list = images.computeIfAbsent(productId, ignored -> new ArrayList<>());
            list.add(image(r, productId, list.isEmpty(), versions.get(productId)));
        });
        return images;
    }

    private Image image(ResultSet r, UUID productId, boolean primary, long version) throws SQLException {
        UUID imageId = r.getObject("id", UUID.class);
        String mode = r.getString("mode");
        int sortOrder = r.getInt("sort_order");
        String alt = r.getString("alt_text");
        if ("LINK".equals(mode)) return new LinkedImage(imageId, alt, sortOrder, primary, version,
                mode, r.getString("source_url"), r.getString("source_name"), r.getString("license"));
        return new UploadedImage(imageId, alt, sortOrder, primary, version, mode,
                "/products/" + productId + "/images/" + imageId + "/content", r.getString("mime_type"),
                r.getInt("byte_size"), r.getInt("width"), r.getInt("height"));
    }
    private record Core(UUID id, UUID sellerId, UUID categoryId, String name, String description,
            boolean published, long version) {}
}

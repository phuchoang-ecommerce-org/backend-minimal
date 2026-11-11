package com.phucle.ecommerce.discovery;

import com.phucle.ecommerce.catalog.api.PublicCatalogReadPort;
import com.phucle.ecommerce.inventory.api.PublicStockReadPort;
import com.phucle.ecommerce.review.api.PublicReviewSummaryPort;
import com.phucle.ecommerce.shared.CursorCodec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
final class DiscoveryQueryHandler implements DiscoveryQueryPort {
    private final PublicCatalogReadPort catalog;
    private final PublicStockReadPort inventory;
    private final PublicReviewSummaryPort reviews;
    private final CursorCodec cursors;

    DiscoveryQueryHandler(PublicCatalogReadPort catalog, PublicStockReadPort inventory,
            PublicReviewSummaryPort reviews, CursorCodec cursors) {
        this.catalog = catalog; this.inventory = inventory; this.reviews = reviews; this.cursors = cursors;
    }

    @Override public Page<Category> categories(String cursor, int limit) {
        checkLimit(limit);
        UUID boundary = cursor == null ? null : cursors.decode(cursor, "categories", "");
        List<PublicCatalogReadPort.Category> rows = catalog.categoriesBefore(boundary, limit + 1);
        boolean more = rows.size() > limit;
        List<PublicCatalogReadPort.Category> selected = more ? rows.subList(0, limit) : rows;
        List<Category> items = selected.stream().map(c -> new Category(c.id(), c.name(), c.version())).toList();
        return new Page<>(items, more ? cursors.encode("categories", "", selected.getLast().id()) : null, more);
    }

    @Override public Page<Product> products(String q, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice,
            String cursor, int limit) {
        checkLimit(limit);
        if (q != null && (q.isBlank() || q.length() > 200)) throw new IllegalArgumentException("Invalid query");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("Invalid price range");
        String filter = String.join(";", q == null ? "" : q, categoryId == null ? "" : categoryId.toString(),
                minPrice == null ? "" : minPrice.toPlainString(), maxPrice == null ? "" : maxPrice.toPlainString());
        UUID boundary = cursor == null ? null : cursors.decode(cursor, "products", filter);
        List<PublicCatalogReadPort.Product> rows = catalog.productsBefore(
                new PublicCatalogReadPort.Filters(q, categoryId, minPrice, maxPrice), boundary, limit + 1);
        boolean more = rows.size() > limit;
        List<PublicCatalogReadPort.Product> selected = more ? rows.subList(0, limit) : rows;
        List<Product> items = compose(selected);
        return new Page<>(items, more ? cursors.encode("products", filter, selected.getLast().id()) : null, more);
    }

    @Override public Product product(UUID id) {
        PublicCatalogReadPort.Product core = catalog.product(id);
        if (core == null) throw new PublicProductNotFoundException();
        return compose(List.of(core)).getFirst();
    }

    private List<Product> compose(List<PublicCatalogReadPort.Product> cores) {
        if (cores.isEmpty()) return List.of();
        Set<UUID> productIds = cores.stream().map(PublicCatalogReadPort.Product::id).collect(Collectors.toSet());
        Set<UUID> variantIds = cores.stream().flatMap(c -> c.variants().stream())
                .map(PublicCatalogReadPort.Variant::id).collect(Collectors.toSet());
        var stock = inventory.findStock(variantIds);
        var summaries = reviews.summaries(productIds);
        return cores.stream().map(c -> {
            var summary = summaries.get(c.id());
            List<Variant> variants = c.variants().stream().map(v -> {
                var current = stock.get(v.id());
                return new Variant(v.id(), v.sku(), v.label(), v.priceUsd().toPlainString(),
                        new Stock(v.id(), current == null ? 0 : current.onHand(), current == null ? 0 : current.version()));
            }).toList();
            List<Image> images = c.images().stream().map(this::image).toList();
            return new Product(c.id(), c.sellerId(), c.categoryId(), c.name(), c.description(), c.published(),
                    c.version(), variants, summary == null ? null : summary.ratingAverage(),
                    summary == null ? 0 : summary.reviewCount(), images);
        }).toList();
    }

    private Image image(PublicCatalogReadPort.Image source) {
        if (source instanceof PublicCatalogReadPort.LinkedImage link)
            return new LinkedImage(link.id(), link.altText(), link.sortOrder(), link.primary(),
                    link.productVersion(), link.mode(), link.url(), link.sourcePage(), link.licenseUrl());
        PublicCatalogReadPort.UploadedImage upload = (PublicCatalogReadPort.UploadedImage) source;
        return new UploadedImage(upload.id(), upload.altText(), upload.sortOrder(), upload.primary(),
                upload.productVersion(), upload.mode(), upload.contentPath(), upload.mimeType(),
                upload.sizeBytes(), upload.width(), upload.height());
    }
    private void checkLimit(int limit) { if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid limit"); }
}

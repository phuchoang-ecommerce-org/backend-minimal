package com.phucle.ecommerce.discovery;

import com.phucle.ecommerce.catalog.api.PublicCatalogReadPort;
import com.phucle.ecommerce.catalog.api.OwnedCatalogReadPort;
import com.phucle.ecommerce.identity.api.SellerShopDirectory;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.inventory.api.PublicStockReadPort;
import com.phucle.ecommerce.review.api.PublicReviewSummaryPort;
import com.phucle.ecommerce.shared.CursorCodec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
class DiscoveryQueryHandler implements DiscoveryQueryPort {
    private final PublicCatalogReadPort catalog;
    private final PublicStockReadPort inventory;
    private final PublicReviewSummaryPort reviews;
    private final CursorCodec cursors;
    private final OwnedCatalogReadPort ownedCatalog;
    private final SellerShopDirectory shops;

    DiscoveryQueryHandler(PublicCatalogReadPort catalog, PublicStockReadPort inventory,
            PublicReviewSummaryPort reviews, CursorCodec cursors, OwnedCatalogReadPort ownedCatalog,
            SellerShopDirectory shops) {
        this.catalog = catalog; this.inventory = inventory; this.reviews = reviews; this.cursors = cursors;
        this.ownedCatalog = ownedCatalog; this.shops = shops;
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    @Override public Page<Product> products(String q, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice,
            String cursor, int limit) {
        checkLimit(limit);
        validateProductFilters(q, minPrice, maxPrice);
        String searchText = q == null || q.isBlank() ? null : q;
        String filter = productFilterKey(searchText, categoryId, minPrice, maxPrice);
        UUID boundary = cursor == null ? null : cursors.decode(cursor, "products", filter);
        List<PublicCatalogReadPort.Product> rows = catalog.productsBefore(
                new PublicCatalogReadPort.Filters(searchText, categoryId, minPrice, maxPrice), boundary, limit + 1);
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override public Product ownProduct(TrustedActor actor, UUID id) {
        if (actor == null || !"SELLER".equals(actor.operationalRole())) throw new SellerProductRoleDeniedException();
        UUID shop = shops.shopIdForAccount(actor.accountId()).orElseThrow(PublicProductNotFoundException::new);
        PublicCatalogReadPort.Product core = ownedCatalog.product(id, shop);
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
        return cores.stream().map(core -> product(core, stock, summaries)).toList();
    }

    private Product product(PublicCatalogReadPort.Product core, Map<UUID, PublicStockReadPort.Stock> stock,
            Map<UUID, PublicReviewSummaryPort.Summary> summaries) {
        var summary = summaries.get(core.id());
        List<Variant> variants = core.variants().stream().map(variant -> variant(variant, stock)).toList();
        List<Image> images = core.images().stream().map(this::image).toList();
        return new Product(core.id(), core.sellerId(), core.categoryId(), core.name(), core.description(),
                core.published(), core.version(), variants, summary == null ? null : summary.ratingAverage(),
                summary == null ? 0 : summary.reviewCount(), images);
    }

    private Variant variant(PublicCatalogReadPort.Variant source, Map<UUID, PublicStockReadPort.Stock> stock) {
        var current = stock.get(source.id());
        return new Variant(source.id(), source.sku(), source.label(), source.priceUsd().toPlainString(),
                new Stock(source.id(), current == null ? 0 : current.onHand(),
                        current == null ? 0 : current.version()));
    }

    private void validateProductFilters(String q, BigDecimal minPrice, BigDecimal maxPrice) {
        if (q != null && q.length() > 200) throw new IllegalArgumentException("Invalid query");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("Invalid price range");
    }

    private String productFilterKey(String q, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
        return String.join(";", q == null ? "" : q, categoryId == null ? "" : categoryId.toString(),
                minPrice == null ? "" : minPrice.toPlainString(), maxPrice == null ? "" : maxPrice.toPlainString());
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

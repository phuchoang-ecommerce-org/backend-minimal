package com.phucle.ecommerce.discovery;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

interface DiscoveryQueryPort {
    record Page<T>(List<T> items, String nextCursor, boolean hasNext) {
        public Page { items = List.copyOf(items); }
    }
    record Category(UUID id, String name, long version) {}
    record Stock(UUID variantId, int onHand, long version) {}
    record Variant(UUID id, String sku, String label, String priceUsd, Stock stock) {}
    sealed interface Image permits LinkedImage, UploadedImage {}
    record LinkedImage(UUID id, String altText, int sortOrder, boolean primary, long productVersion,
            String mode, String url, String sourcePage, String licenseUrl) implements Image {}
    record UploadedImage(UUID id, String altText, int sortOrder, boolean primary, long productVersion,
            String mode, String contentPath, String mimeType, int sizeBytes, int width, int height) implements Image {}
    record Product(UUID id, UUID sellerId, UUID categoryId, String name, String description,
            boolean published, long version, List<Variant> variants, BigDecimal ratingAverage,
            int reviewCount, List<Image> images) {
        public Product { variants = List.copyOf(variants); images = List.copyOf(images); }
    }
    Page<Category> categories(String cursor, int limit);
    Page<Product> products(String q, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice, String cursor, int limit);
    Product product(UUID id);
}

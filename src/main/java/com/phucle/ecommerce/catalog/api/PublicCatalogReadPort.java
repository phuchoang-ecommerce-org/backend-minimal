package com.phucle.ecommerce.catalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PublicCatalogReadPort {
    record Category(UUID id, String name, long version) {}
    record Variant(UUID id, String sku, String label, BigDecimal priceUsd) {}
    sealed interface Image permits LinkedImage, UploadedImage {}
    record LinkedImage(UUID id, String altText, int sortOrder, boolean primary, long productVersion,
            String mode, String url, String sourcePage, String licenseUrl) implements Image {}
    record UploadedImage(UUID id, String altText, int sortOrder, boolean primary, long productVersion,
            String mode, String contentPath, String mimeType, int sizeBytes, int width, int height) implements Image {}
    record Product(UUID id, UUID sellerId, UUID categoryId, String name, String description,
            boolean published, long version, List<Variant> variants, List<Image> images) {
        public Product { variants = List.copyOf(variants); images = List.copyOf(images); }
    }
    record Filters(String q, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice) {}
    List<Category> categoriesBefore(UUID boundary, int fetchLimit);
    List<Product> productsBefore(Filters filters, UUID boundary, int fetchLimit);
    Product product(UUID id);
}

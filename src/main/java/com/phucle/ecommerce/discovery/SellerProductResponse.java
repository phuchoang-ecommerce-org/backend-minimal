package com.phucle.ecommerce.discovery;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Web-only shape of the approved Product response. */
record SellerProductResponse(UUID id, UUID sellerId, UUID categoryId, String name, String description,
        boolean published, long version, List<Variant> variants, BigDecimal ratingAverage,
        int reviewCount, List<Image> images) {
    SellerProductResponse { variants = List.copyOf(variants); images = List.copyOf(images); }
    record Stock(UUID variantId, int onHand, long version) {}
    record Variant(UUID id, String sku, String label, String priceUsd, Stock stock) {}
    sealed interface Image permits LinkedImage, UploadedImage {}
    record LinkedImage(UUID id, String altText, int sortOrder, boolean primary, long productVersion,
            String mode, String url, String sourcePage, String licenseUrl) implements Image {}
    record UploadedImage(UUID id, String altText, int sortOrder, boolean primary, long productVersion,
            String mode, String contentPath, String mimeType, int sizeBytes, int width, int height) implements Image {}
}

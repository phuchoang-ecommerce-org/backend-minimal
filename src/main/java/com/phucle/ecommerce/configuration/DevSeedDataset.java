package com.phucle.ecommerce.configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record DevSeedDataset(List<Category> categories, List<Product> products, List<Address> addresses,
                      List<Order> orders, List<Review> reviews) {
    record Category(UUID id, String name) { }
    record Product(UUID id, UUID categoryId, UUID variantId, String title, String sku,
                   BigDecimal priceUsd, int stock, Image image) { }
    record Image(UUID id, String url, String sourceName, String license, String altText) { }
    record Address(UUID id, String recipient, String addressLine, String locality,
                   String postalCode, String country) { }
    record Order(UUID id, UUID sellerOrderId, UUID lineId, UUID variantId,
                 int quantity, String paymentStatus) { }
    record Review(UUID id, UUID productId, int rating, String body) { }
}

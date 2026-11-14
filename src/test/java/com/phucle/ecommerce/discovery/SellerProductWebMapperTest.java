package com.phucle.ecommerce.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerProductWebMapperTest {
    @Test void mapsFullOwnedProductResponseIncludingImageVariantsAndStock() {
        UUID productId = UUID.randomUUID(), sellerId = UUID.randomUUID(), categoryId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID(), linkId = UUID.randomUUID(), uploadId = UUID.randomUUID();
        var view = new DiscoveryQueryPort.Product(productId, sellerId, categoryId, "Unpublished", "Detail", false, 4,
                List.of(new DiscoveryQueryPort.Variant(variantId, "SKU", "Default", "12.50",
                        new DiscoveryQueryPort.Stock(variantId, 7, 3))), new BigDecimal("4.50"), 2,
                List.of(new DiscoveryQueryPort.LinkedImage(linkId, "Link", 0, true, 4, "LINK",
                                "https://example.test/item", "https://example.test/source", "https://example.test/license"),
                        new DiscoveryQueryPort.UploadedImage(uploadId, "Upload", 1, false, 4, "UPLOAD",
                                "/products/" + productId + "/images/" + uploadId + "/content", "image/png", 10, 2, 2)));
        SellerProductResponse response = new SellerProductWebMapperImpl().toResponse(view);
        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.sellerId()).isEqualTo(sellerId);
        assertThat(response.categoryId()).isEqualTo(categoryId);
        assertThat(response.published()).isFalse();
        assertThat(response.version()).isEqualTo(4);
        assertThat(response.variants()).containsExactly(new SellerProductResponse.Variant(variantId, "SKU", "Default",
                "12.50", new SellerProductResponse.Stock(variantId, 7, 3)));
        assertThat(response.ratingAverage()).isEqualByComparingTo("4.50");
        assertThat(response.reviewCount()).isEqualTo(2);
        assertThat(response.images()).hasSize(2);
        assertThat(response.images().get(0)).isInstanceOf(SellerProductResponse.LinkedImage.class);
        assertThat(((SellerProductResponse.LinkedImage) response.images().get(0)).url())
                .isEqualTo("https://example.test/item");
        assertThat(response.images().get(1)).isInstanceOf(SellerProductResponse.UploadedImage.class);
        assertThat(((SellerProductResponse.UploadedImage) response.images().get(1)).mimeType())
                .isEqualTo("image/png");
    }
}

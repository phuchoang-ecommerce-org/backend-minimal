package com.phucle.ecommerce.discovery;
import com.phucle.ecommerce.shared.CursorCodec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phucle.ecommerce.catalog.api.PublicCatalogReadPort;
import com.phucle.ecommerce.catalog.api.OwnedCatalogReadPort;
import com.phucle.ecommerce.identity.api.SellerShopDirectory;
import com.phucle.ecommerce.inventory.api.PublicStockReadPort;
import com.phucle.ecommerce.review.api.PublicReviewSummaryPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryQueryHandlerTest {
    @Test void listComposesBatchProjectionWithoutDetailLookupsAndCopiesResults() {
        var catalog = mock(PublicCatalogReadPort.class);
        var inventory = mock(PublicStockReadPort.class);
        var reviews = mock(PublicReviewSummaryPort.class);
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        var variants = new ArrayList<PublicCatalogReadPort.Variant>();
        variants.add(new PublicCatalogReadPort.Variant(variantId, "SKU", "One", new BigDecimal("12.50")));
        var core = new PublicCatalogReadPort.Product(productId, UUID.randomUUID(), UUID.randomUUID(),
                "Product", "Description", true, 0, variants, List.of());
        when(catalog.productsBefore(any(), eq(null), eq(21))).thenReturn(List.of(core));
        when(inventory.findStock(any())).thenReturn(Map.of(variantId, new PublicStockReadPort.Stock(variantId, 7, 0)));
        when(reviews.summaries(any())).thenReturn(Map.of());
        var handler = new DiscoveryQueryHandler(catalog, inventory, reviews,
                new CursorCodec("test-only-cursor-mac-secret-at-least-32-bytes"),
                mock(OwnedCatalogReadPort.class), mock(SellerShopDirectory.class));

        var page = handler.products(null, null, null, null, null, 20);
        variants.clear();
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().variants()).hasSize(1);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> page.items().getFirst().variants().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(catalog, never()).product(any());
    }
}

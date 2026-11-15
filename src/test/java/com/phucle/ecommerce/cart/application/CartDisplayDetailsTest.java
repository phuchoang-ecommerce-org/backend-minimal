package com.phucle.ecommerce.cart.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phucle.ecommerce.cart.application.port.out.CartReadPort;
import com.phucle.ecommerce.catalog.api.CartOfferReadPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CartDisplayDetailsTest {
    @Test void cartResponseIncludesNamesForDisplay() throws Exception {
        UUID variant = UUID.randomUUID();
        CartReadPort read = mock(CartReadPort.class);
        CartOfferReadPort offers = mock(CartOfferReadPort.class);
        when(read.guest("guest")).thenReturn(new CartReadPort.Snapshot(1, false, List.of(new CartReadPort.Line(variant, 2))));
        when(offers.offers(Set.of(variant))).thenReturn(Map.of(variant,
                new CartOfferReadPort.Offer(variant, new BigDecimal("12.50"), true, "Canvas Bag", "Blue")));
        String json = new ObjectMapper().writeValueAsString(new CartQueryService(read, offers).existingGuest("guest"));
        assertThat(json).contains("\"productName\":\"Canvas Bag\"")
                .contains("\"variantLabel\":\"Blue\"");
    }
}

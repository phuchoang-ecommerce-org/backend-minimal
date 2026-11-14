package com.phucle.ecommerce.inventory.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phucle.ecommerce.inventory.application.SellerStockCommands;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerStockWebMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SellerStockWebMapper mapper = new SellerStockWebMapperImpl();

    @Test void mapsValidatedPayloadAndResultWithoutActorFields() throws Exception {
        StockUpdateRequest request = StockUpdateRequest.parse(json.readTree("{\"onHand\":7,\"expectedVersion\":3}"));
        assertThat(mapper.toPayload(request)).isEqualTo(new SellerStockCommands.Update(7, 3));
        UUID variantId = UUID.randomUUID();
        assertThat(mapper.toResponse(new SellerStockCommands.Stock(variantId, 7, 4)))
                .isEqualTo(new StockResponse(variantId, 7, 4));
        assertThatThrownBy(() -> StockUpdateRequest.parse(json.readTree("{\"onHand\":7.5,\"expectedVersion\":3}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockUpdateRequest.parse(json.readTree("{\"onHand\":7,\"expectedVersion\":3,\"sellerId\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

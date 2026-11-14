package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.phucle.ecommerce.inventory.application.StockWritePort;
import com.phucle.ecommerce.inventory.domain.StockItem;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerStockRefactorBoundaryTest {
    @Test void writePortAcceptsDomainTransition() throws Exception {
        var method = StockWritePort.class.getMethod("replace", StockItem.class, StockItem.class);
        assertThat(method.getReturnType()).isEqualTo(StockItem.class);
    }

    @Test void sellerRoutesUseWebResponses() throws Exception {
        var stockController = Class.forName("com.phucle.ecommerce.inventory.adapter.in.web.SellerStockController");
        var stockResponse = Class.forName("com.phucle.ecommerce.inventory.adapter.in.web.StockResponse");
        var stockRequest = Class.forName("com.phucle.ecommerce.inventory.adapter.in.web.StockUpdateRequest");
        var stockMapper = Class.forName("com.phucle.ecommerce.inventory.adapter.in.web.SellerStockWebMapperImpl");
        assertThat(stockController.getDeclaredMethod("replace", UUID.class,
                Class.forName("com.fasterxml.jackson.databind.JsonNode")).getReturnType()).isEqualTo(stockResponse);
        assertThat(stockRequest.isRecord()).isTrue();
        assertThat(stockMapper).isNotNull();

        var productController = Class.forName("com.phucle.ecommerce.discovery.DiscoveryController");
        var productResponse = Class.forName("com.phucle.ecommerce.discovery.SellerProductResponse");
        var productMapper = Class.forName("com.phucle.ecommerce.discovery.SellerProductWebMapperImpl");
        assertThat(productController.getDeclaredMethod("ownProduct", UUID.class).getReturnType())
                .isEqualTo(productResponse);
        assertThat(productMapper).isNotNull();
    }
}

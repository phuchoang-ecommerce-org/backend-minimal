package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CheckoutTableOwnershipTest {
    @Test void orderingJdbcAdapterTouchesOnlyOrderingTables() throws Exception {
        Path root = Path.of("src/main/java/com/phucle/ecommerce/ordering/adapter/out/jdbc");
        for (Path file : Files.walk(root).filter(p -> p.toString().endsWith(".java")).toList()) {
            String source = Files.readString(file);
            assertThat(source).doesNotContain("marketplace.cart", "marketplace.address", "marketplace.product",
                    "marketplace.variant", "marketplace.seller_shop", "marketplace.stock", "marketplace.audit_entry");
        }
    }
}

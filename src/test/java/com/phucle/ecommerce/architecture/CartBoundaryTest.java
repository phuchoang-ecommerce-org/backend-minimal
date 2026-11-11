package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CartBoundaryTest {
    @Test void cartJdbcOwnsOnlyCartTablesAndApplicationHasNoSql() throws Exception {
        Path root=Path.of("src/main/java/com/phucle/ecommerce/cart");
        try (var files=Files.walk(root)) {
            for (Path file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                String source=Files.readString(file);
                if (file.toString().contains("adapter/out/jdbc")) {
                    assertThat(source).as(file.toString()).doesNotContain("marketplace.variant",
                            "marketplace.product", "marketplace.seller_shop", "marketplace.stock",
                            "marketplace.account");
                } else {
                    assertThat(source).as(file.toString()).doesNotContain("JdbcTemplate", "marketplace.variant",
                            "marketplace.product", "marketplace.seller_shop", "marketplace.stock",
                            "marketplace.cart_line", "marketplace.account");
                }
            }
        }
    }
}

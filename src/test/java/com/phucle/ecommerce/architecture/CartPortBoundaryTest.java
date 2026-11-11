package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CartPortBoundaryTest {
    @Test void cartUsesExplicitPortsAndAdapterPackages() throws Exception {
        Path root=Path.of("src/main/java/com/phucle/ecommerce/cart");
        assertThat(Files.exists(root.resolve("application/port/out/CartRepositoryPort.java"))).isTrue();
        assertThat(Files.exists(root.resolve("application/port/in/CartCommands.java"))).isTrue();
        assertThat(Files.exists(root.resolve("application/port/in/CartQueries.java"))).isTrue();
        assertThat(Files.exists(root.resolve("adapter/in/web/CartController.java"))).isTrue();
        String commands=Files.readString(root.resolve("application/CartCommandService.java"));
        String queries=Files.readString(root.resolve("application/CartQueryService.java"));
        assertThat(commands).doesNotContain("adapter.out.jdbc.CartRepository");
        assertThat(queries).doesNotContain("adapter.out.jdbc.CartRepository");
        assertThat(commands).doesNotContain("import com.phucle.ecommerce.cart.CartSettings;");
        String controller=Files.readString(root.resolve("adapter/in/web/CartController.java"));
        assertThat(controller).doesNotContain("CartCommandService", "CartQueryService", "CartSettings");
    }
}

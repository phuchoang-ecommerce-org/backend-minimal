package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.phucle.ecommerce.ordering.application.OwnOrderQueries;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OwnOrderReadBoundaryTest {
    @Test void inputApiAndWebMapperDoNotExposeOutputPortTypes() throws Exception {
        for (var method : OwnOrderQueries.class.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain("OwnOrderReadPort");
        }
        for (var component : OwnOrderQueries.Page.class.getRecordComponents()) {
            assertThat(component.getGenericType().getTypeName()).doesNotContain("OwnOrderReadPort");
        }
        String mapper = Files.readString(Path.of("src/main/java/com/phucle/ecommerce/ordering/adapter/in/web/CheckoutWebMapper.java"));
        assertThat(mapper).doesNotContain("OwnOrderReadPort");
    }
}

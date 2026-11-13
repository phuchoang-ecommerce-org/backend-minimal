package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.phucle.ecommerce.ordering.application.CancelOrderService;
import com.phucle.ecommerce.ordering.application.CancelOrderResultReadPort;
import com.phucle.ecommerce.ordering.application.OwnOrderQueries;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CancelOrderCommandBoundaryTest {
    @Test void commandDoesNotCallQueryUseCase() {
        var dependencies = Arrays.stream(CancelOrderService.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .toList();
        assertThat(dependencies).doesNotContain(OwnOrderQueries.class).contains(CancelOrderResultReadPort.class);
    }
}

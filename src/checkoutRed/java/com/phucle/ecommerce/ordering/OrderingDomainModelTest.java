package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import com.phucle.ecommerce.ordering.domain.OrderFactory;
import com.phucle.ecommerce.ordering.domain.OrderPlacementLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderingDomainModelTest {
    @Test void partialCodPaymentRemainsPendingUntilEveryPortionIsPaid() {
        CustomerOrder order = twoSellerOrder();
        var first = order.portions().get(0);
        var second = order.portions().get(1);
        Instant paid = Instant.parse("2026-11-13T00:00:00Z");
        order.recordCod(first.id(), first.sellerId(), paid);
        assertThat(order.paymentStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(first.paidAt()).isEqualTo(paid);
        assertThatThrownBy(() -> order.recordCod(first.id(), first.sellerId(), paid))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> order.recordCod(second.id(), first.sellerId(), paid))
                .isInstanceOf(IllegalArgumentException.class);
        order.recordCod(second.id(), second.sellerId(), paid);
        assertThat(order.paymentStatus()).isEqualTo("PAID");
        assertThatThrownBy(() -> order.cancel(paid)).isInstanceOf(IllegalStateException.class);
    }

    @Test void cancellationIsIdempotentAndPreventsLaterPayment() {
        CustomerOrder order = twoSellerOrder();
        Instant time = Instant.parse("2026-11-13T00:00:00Z");
        assertThat(order.cancel(time)).isTrue();
        assertThat(order.cancel(time.plusSeconds(10))).isFalse();
        assertThat(order.cancelledAt()).isEqualTo(time);
        assertThat(order.status()).isEqualTo("CANCELLED");
        var first = order.portions().getFirst();
        assertThatThrownBy(() -> order.recordCod(first.id(), first.sellerId(), time))
                .isInstanceOf(IllegalStateException.class);
    }

    private CustomerOrder twoSellerOrder() {
        return OrderFactory.place(UUID.randomUUID(), new AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN"),
                List.of(new OrderPlacementLine(UUID.randomUUID(), UUID.fromString("00000000-0000-4000-8000-000000000301"),
                                UUID.randomUUID(), "First", "first", 1, new BigDecimal("12.50"), new BigDecimal("0.00")),
                        new OrderPlacementLine(UUID.randomUUID(), UUID.fromString("00000000-0000-4000-8000-000000000302"),
                                UUID.randomUUID(), "Second", "second", 1, new BigDecimal("7.25"), new BigDecimal("0.00"))));
    }
}

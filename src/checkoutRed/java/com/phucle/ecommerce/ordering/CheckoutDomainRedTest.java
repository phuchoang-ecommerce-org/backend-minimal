package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phucle.ecommerce.ordering.domain.OrderFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutDomainRedTest {
    @Test
    void groupsTwoSellersAndSumsUsdWithoutRoundingAwayCents() {
        UUID firstSeller = UUID.fromString("00000000-0000-4000-8000-000000000101");
        UUID secondSeller = UUID.fromString("00000000-0000-4000-8000-000000000102");
        UUID member = UUID.fromString("00000000-0000-4000-8000-000000000103");
        var address = new OrderFactory.AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN");
        var draft = OrderFactory.place(member, address, List.of(
                new OrderFactory.Line(UUID.randomUUID(), firstSeller, UUID.randomUUID(), "First", "first-sku",
                        2, new BigDecimal("12.50"), new BigDecimal("0.00")),
                new OrderFactory.Line(UUID.randomUUID(), secondSeller, UUID.randomUUID(), "Second", "second-sku",
                        1, new BigDecimal("7.25"), new BigDecimal("0.00"))));
        assertThat(draft.memberId()).isEqualTo(member);
        assertThat(draft.status()).isEqualTo("PLACED");
        assertThat(draft.address()).isEqualTo(address);
        assertThat(draft.totalUsd()).isEqualByComparingTo("32.25");
        assertThat(draft.portions()).hasSize(2);
        assertThat(draft.portions()).extracting(OrderFactory.Portion::sellerId)
                .containsExactlyInAnyOrder(firstSeller, secondSeller);
        assertThat(draft.portions()).extracting(OrderFactory.Portion::totalUsd)
                .containsExactlyInAnyOrder(new BigDecimal("25.00"), new BigDecimal("7.25"));
        assertThat(draft.portions()).extracting(OrderFactory.Portion::paymentStatus)
                .containsOnly("PENDING_PAYMENT");
        assertThat(draft.portions().stream().flatMap(portion -> portion.lines().stream())
                .map(line -> line.source().sku()).toList()).containsExactlyInAnyOrder("first-sku", "second-sku");
    }

    @Test
    void allocatedDiscountIsFrozenInLineAndPortionTotals() {
        UUID member = UUID.fromString("00000000-0000-4000-8000-000000000104");
        UUID seller = UUID.fromString("00000000-0000-4000-8000-000000000105");
        var address = new OrderFactory.AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN");
        var draft = OrderFactory.place(member, address, List.of(
                new OrderFactory.Line(UUID.randomUUID(), seller, UUID.randomUUID(), "Product", "sku",
                        2, new BigDecimal("12.50"), new BigDecimal("1.25"))));
        assertThat(draft.totalUsd()).isEqualByComparingTo("23.75");
        assertThat(draft.portions().getFirst().totalUsd()).isEqualByComparingTo("23.75");
        assertThat(draft.portions().getFirst().lines().getFirst().source().discountUsd())
                .isEqualByComparingTo("1.25");
        assertThat(draft.portions().getFirst().lines().getFirst().lineTotalUsd())
                .isEqualByComparingTo("23.75");
    }

    @Test
    void emptyCheckoutCannotCreateAnOrderRoot() {
        var address = new OrderFactory.AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN");
        assertThatThrownBy(() -> OrderFactory.place(UUID.randomUUID(), address, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

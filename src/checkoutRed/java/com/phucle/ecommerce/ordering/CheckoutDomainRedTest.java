package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phucle.ecommerce.ordering.domain.OrderFactory;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import com.phucle.ecommerce.ordering.domain.SellerOrder;
import com.phucle.ecommerce.ordering.domain.OrderPlacementLine;
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
        var address = new AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN");
        var draft = OrderFactory.place(member, address, List.of(
                new OrderPlacementLine(UUID.randomUUID(), firstSeller, UUID.randomUUID(), "First", "first-sku",
                        2, new BigDecimal("12.50"), new BigDecimal("0.00")),
                new OrderPlacementLine(UUID.randomUUID(), secondSeller, UUID.randomUUID(), "Second", "second-sku",
                        1, new BigDecimal("7.25"), new BigDecimal("0.00"))));
        assertThat(draft.memberId()).isEqualTo(member);
        assertThat(draft.status()).isEqualTo("PLACED");
        assertThat(draft.address()).isEqualTo(address);
        assertThat(draft.totalUsd()).isEqualByComparingTo("32.25");
        assertThat(draft.portions()).hasSize(2);
        assertThat(draft.portions()).allSatisfy(portion -> {
            assertThat(portion).hasFieldOrProperty("id");
            assertThat(portion).extracting("id").isNotNull();
            assertThat(portion.lines()).allSatisfy(line -> {
                assertThat(line).hasFieldOrProperty("id");
                assertThat(line).extracting("id").isNotNull();
                assertThat(line.snapshot().sellerId()).isEqualTo(portion.sellerId());
            });
        });
        assertThat(draft.portions()).extracting(SellerOrder::sellerId)
                .containsExactlyInAnyOrder(firstSeller, secondSeller);
        assertThat(draft.portions()).extracting(SellerOrder::totalUsd)
                .containsExactlyInAnyOrder(new BigDecimal("25.00"), new BigDecimal("7.25"));
        assertThat(draft.portions()).extracting(SellerOrder::paymentStatus)
                .containsOnly("PENDING_PAYMENT");
        assertThat(draft.portions().stream().flatMap(portion -> portion.lines().stream())
                .map(line -> line.snapshot().sku()).toList()).containsExactlyInAnyOrder("first-sku", "second-sku");
    }

    @Test
    void allocatedDiscountIsFrozenInLineAndPortionTotals() {
        UUID member = UUID.fromString("00000000-0000-4000-8000-000000000104");
        UUID seller = UUID.fromString("00000000-0000-4000-8000-000000000105");
        var address = new AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN");
        var draft = OrderFactory.place(member, address, List.of(
                new OrderPlacementLine(UUID.randomUUID(), seller, UUID.randomUUID(), "Product", "sku",
                        2, new BigDecimal("12.50"), new BigDecimal("1.25"))));
        assertThat(draft.totalUsd()).isEqualByComparingTo("23.75");
        assertThat(draft.portions().getFirst().totalUsd()).isEqualByComparingTo("23.75");
        assertThat(draft.portions().getFirst().lines().getFirst().snapshot().discount().amount())
                .isEqualByComparingTo("1.25");
        assertThat(draft.portions().getFirst().lines().getFirst().snapshot().total().amount())
                .isEqualByComparingTo("23.75");
    }

    @Test
    void emptyCheckoutCannotCreateAnOrderRoot() {
        var address = new AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN");
        assertThatThrownBy(() -> OrderFactory.place(UUID.randomUUID(), address, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phucle.ecommerce.audit.api.OrderAudit;
import com.phucle.ecommerce.cart.api.CheckoutCart;
import com.phucle.ecommerce.catalog.api.CheckoutOffers;
import com.phucle.ecommerce.identity.api.AddressView;
import com.phucle.ecommerce.identity.api.OwnedAddressQueries;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.inventory.api.CheckoutStock;
import com.phucle.ecommerce.ordering.application.CheckoutCommandService;
import com.phucle.ecommerce.ordering.application.CreationSnapshotReadPort;
import com.phucle.ecommerce.ordering.application.OrderWritePort;
import com.phucle.ecommerce.ordering.application.PlaceCodOrder;
import com.phucle.ecommerce.ordering.domain.OrderFactory;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CheckoutApplicationRedTest {
    @Test void memberCommandReturnsPlacedOrderIdentity() {
        var actor = new TrustedActor(UUID.fromString("00000000-0000-4000-8000-000000000201"), "MEMBER", "checkout-red");
        UUID addressId = UUID.fromString("00000000-0000-4000-8000-000000000202");
        UUID variant = UUID.fromString("00000000-0000-4000-8000-000000000203");
        UUID product = UUID.fromString("00000000-0000-4000-8000-000000000204");
        UUID sellerShop = UUID.fromString("00000000-0000-4000-8000-000000000205");
        UUID cartId = UUID.fromString("00000000-0000-4000-8000-000000000206");
        var command = new PlaceCodOrder.Command(addressId, 1, "COD", null, "application-red-key");
        var writes = mock(OrderWritePort.class);
        var reads = mock(CreationSnapshotReadPort.class);
        var carts = mock(CheckoutCart.class);
        var addresses = mock(OwnedAddressQueries.class);
        var offers = mock(CheckoutOffers.class);
        var stocks = mock(CheckoutStock.class);
        var audit = mock(OrderAudit.class);
        when(writes.findExisting(actor.accountId(), command.idempotencyKey())).thenReturn(Optional.empty());
        when(carts.lockOwned(actor.accountId())).thenReturn(new CheckoutCart.Locked(cartId, 1,
                List.of(new CheckoutCart.Line(variant, 2))));
        when(addresses.findOwned(actor, addressId)).thenReturn(
                new AddressView(addressId, "Buyer", "1 Main St", "Hanoi", "10000", "VN"));
        when(offers.lockCurrent(List.of(variant))).thenReturn(List.of(new CheckoutOffers.Offer(product, sellerShop,
                variant, "Product", "sku", new BigDecimal("12.50"), true)));
        when(stocks.decrease(any())).thenReturn(true);
        when(reads.readCreation(any(UUID.class), isNull())).thenAnswer(invocation ->
                new CreationSnapshotReadPort.Order(invocation.getArgument(0), actor.accountId(), "PLACED",
                        "PENDING_PAYMENT", new BigDecimal("25.00"), List.of(), Instant.now(),
                        new AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN"),
                        new BigDecimal("0.00"), "COD", null));
        var result = new CheckoutCommandService(writes, reads, carts, addresses, offers, stocks, audit)
                .execute(actor, command);
        assertThat(result.id()).isNotNull();
        assertThat(result.status()).isEqualTo("PLACED");
        verify(writes).serializeIntent(actor.accountId(), command.idempotencyKey());
        verify(stocks).decrease(java.util.Map.of(variant, 2));
        verify(carts).consume(cartId, 1);
        var aggregate = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(writes).save(eq(command.idempotencyKey()), any(String.class), aggregate.capture());
        assertThat(aggregate.getValue().id()).isEqualTo(result.id());
        verify(audit).placed(actor.accountId(), result.id());
    }
}

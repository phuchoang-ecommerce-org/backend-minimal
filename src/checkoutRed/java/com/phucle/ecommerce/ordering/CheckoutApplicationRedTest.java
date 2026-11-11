package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.ordering.application.PlaceOrder;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CheckoutApplicationRedTest {
    @Test
    void memberCommandReturnsPlacedOrderIdentity() {
        var actor = new TrustedActor(UUID.fromString("00000000-0000-4000-8000-000000000201"), "MEMBER", "checkout-red");
        var command = new PlaceOrder.Command(
                UUID.fromString("00000000-0000-4000-8000-000000000202"), 1, "COD", null, "application-red-key");
        var orders = mock(PlaceOrder.Orders.class);
        var carts = mock(PlaceOrder.Carts.class);
        var addresses = mock(PlaceOrder.Addresses.class);
        var offers = mock(PlaceOrder.Offers.class);
        var stocks = mock(PlaceOrder.Stocks.class);
        var vouchers = mock(PlaceOrder.Vouchers.class);
        var audit = mock(PlaceOrder.Audit.class);
        UUID variant = UUID.fromString("00000000-0000-4000-8000-000000000203");
        when(orders.findReplay(actor.accountId(), command.idempotencyKey(), command)).thenReturn(Optional.empty());
        when(carts.lockOwned(actor.accountId())).thenReturn(new PlaceOrder.CartIntent(1, List.of(variant)));
        when(addresses.findOwned(actor.accountId(), command.addressId())).thenReturn(
                new PlaceOrder.AddressSnapshot("Buyer", "1 Main St", "Hanoi", "10000", "VN"));
        var result = new PlaceOrder(orders, carts, addresses, offers, stocks, vouchers, audit).execute(actor, command);
        assertThat(result.orderId()).isNotNull();
        assertThat(result.status()).isEqualTo("PLACED");
        verify(offers).requirePurchasable(List.of(variant));
        verify(stocks).decrease(List.of(variant));
        verify(carts).consume(actor.accountId(), 1);
        verify(orders).save(actor.accountId(), command.idempotencyKey(), command, result);
        verify(audit).append(actor.accountId(), result.orderId());
    }
}

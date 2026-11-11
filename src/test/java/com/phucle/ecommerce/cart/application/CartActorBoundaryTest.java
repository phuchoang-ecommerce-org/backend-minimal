package com.phucle.ecommerce.cart.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.phucle.ecommerce.cart.application.port.out.CartReadPort;
import com.phucle.ecommerce.cart.application.port.out.CartRepositoryPort;
import com.phucle.ecommerce.cart.application.port.out.GuestSessionPolicy;
import com.phucle.ecommerce.cart.domain.CartFailure;
import com.phucle.ecommerce.catalog.api.CartOfferReadPort;
import com.phucle.ecommerce.inventory.api.PublicStockReadPort;
import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartActorBoundaryTest {
    @Test void directSellerSetCannotReadOrCreateMemberCart() {
        var repository = mock(CartRepositoryPort.class);
        var service = new CartCommandService(repository, mock(GuestCartProjection.class),
                mock(CartOfferReadPort.class), mock(PublicStockReadPort.class), mock(GuestSessionPolicy.class));
        assertThatThrownBy(() -> service.setLine(seller(), "guest-proof", UUID.randomUUID(), 1, 0))
                .isInstanceOf(CartFailure.class).hasMessage("ROLE_DENIED");
        verifyNoInteractions(repository);
    }

    @Test void directSellerRemoveCannotReadMemberCart() {
        var repository = mock(CartRepositoryPort.class);
        var service = new CartCommandService(repository, mock(GuestCartProjection.class),
                mock(CartOfferReadPort.class), mock(PublicStockReadPort.class), mock(GuestSessionPolicy.class));
        assertThatThrownBy(() -> service.removeLine(seller(), "guest-proof", UUID.randomUUID(), 0))
                .isInstanceOf(CartFailure.class).hasMessage("ROLE_DENIED");
        verifyNoInteractions(repository);
    }

    @Test void directSellerQueryCannotReadMemberCart() {
        var read = mock(CartReadPort.class);
        var service = new CartQueryService(read, mock(CartOfferReadPort.class));
        assertThatThrownBy(() -> service.getMemberCart(seller()))
                .isInstanceOf(CartFailure.class).hasMessage("ROLE_DENIED");
        verifyNoInteractions(read);
    }

    private TrustedActor seller() {
        return new TrustedActor(UUID.randomUUID(), "SELLER", "cart-actor-test");
    }
}

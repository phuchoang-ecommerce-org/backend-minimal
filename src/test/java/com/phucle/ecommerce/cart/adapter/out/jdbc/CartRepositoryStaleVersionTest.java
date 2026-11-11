package com.phucle.ecommerce.cart.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.startsWith;

import com.phucle.ecommerce.cart.domain.Cart;
import com.phucle.ecommerce.cart.domain.CartFailure;
import com.phucle.ecommerce.cart.domain.CartOwner;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CartRepositoryStaleVersionTest {
    @Test void staleUpdateRollsBackSave() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        Cart cart = new Cart(UUID.randomUUID(), new CartOwner.Member(UUID.randomUUID()), 1, null, List.of());
        assertThatThrownBy(() -> new CartRepository(jdbc).save(cart, 0))
                .isInstanceOf(CartFailure.class)
                .extracting(e -> ((CartFailure) e).code()).isEqualTo(CartFailure.Code.STALE_VERSION);
        verify(jdbc, never()).update(startsWith("DELETE FROM marketplace.cart_line"), any(Object[].class));
    }
}

package com.phucle.ecommerce.cart.application;

import com.phucle.ecommerce.cart.application.port.in.CartQueries;
import com.phucle.ecommerce.cart.application.port.in.CartView;
import com.phucle.ecommerce.cart.application.port.out.CartRepositoryPort;
import com.phucle.ecommerce.cart.domain.Cart;
import com.phucle.ecommerce.catalog.api.CartOfferReadPort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartQueryService implements CartQueries {
    private final CartRepositoryPort repository;
    private final CartOfferReadPort offers;
    public CartQueryService(CartRepositoryPort repository,CartOfferReadPort offers) {
        this.repository=repository;this.offers=offers;
    }
    @Transactional(readOnly=true)
    @Override public CartView getMemberCart(UUID memberId) { return view(repository.member(memberId,false)); }
    public CartView view(Cart cart) {
        if (cart==null) return new CartView(List.of(),"0.00",0);
        Set<UUID> ids=cart.lines().stream().map(l->l.variantId()).collect(Collectors.toSet());
        var current=offers.offers(ids);
        List<CartView.Line> lines=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for (var line:cart.lines()) {
            var offer=current.get(line.variantId());
            if (offer==null) throw new IllegalStateException("Stored cart variant is missing");
            lines.add(new CartView.Line(line.variantId(),line.quantity().value(),offer.priceUsd().toPlainString()));
            total=total.add(offer.priceUsd().multiply(BigDecimal.valueOf(line.quantity().value())));
        }
        return new CartView(lines,total.setScale(2).toPlainString(),cart.version());
    }
}

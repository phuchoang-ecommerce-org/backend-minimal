package com.phucle.ecommerce.cart.application;

import com.phucle.ecommerce.cart.application.port.in.CartQueries;
import com.phucle.ecommerce.cart.application.port.in.CartView;
import com.phucle.ecommerce.cart.application.port.out.CartReadPort;
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
    private final CartReadPort read;
    private final CartOfferReadPort offers;
    public CartQueryService(CartReadPort read,CartOfferReadPort offers) {
        this.read=read;this.offers=offers;
    }
    @Transactional(readOnly=true)
    @Override public CartView getMemberCart(UUID memberId) { return view(read.member(memberId)); }
    @Transactional(readOnly=true)
    public CartView existingGuest(String hash) {
        var snapshot = read.guest(hash);
        return snapshot == null || snapshot.consumed() ? null : view(snapshot);
    }
    private CartView view(CartReadPort.Snapshot snapshot) {
        if (snapshot == null) return new CartView(List.of(),"0.00",0);
        Set<UUID> ids=snapshot.lines().stream().map(CartReadPort.Line::variantId).collect(Collectors.toSet());
        var current=offers.offers(ids);
        List<CartView.Line> lines=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for (var line:snapshot.lines()) {
            var offer=current.get(line.variantId());
            if (offer==null) throw new IllegalStateException("Stored cart variant is missing");
            lines.add(new CartView.Line(line.variantId(),line.quantity(),offer.priceUsd().toPlainString()));
            total=total.add(offer.priceUsd().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return new CartView(lines,total.setScale(2).toPlainString(),snapshot.version());
    }
}

package com.phucle.ecommerce.cart.application;

import com.phucle.ecommerce.cart.application.port.out.GuestSessionPolicy;
import com.phucle.ecommerce.cart.application.port.in.CartCommands;
import com.phucle.ecommerce.cart.application.port.in.CartView;
import com.phucle.ecommerce.cart.application.port.in.GuestCart;
import com.phucle.ecommerce.cart.application.port.out.CartRepositoryPort;
import com.phucle.ecommerce.cart.api.GuestCartMergeCommand;
import com.phucle.ecommerce.cart.api.GuestCartMergeResult;
import com.phucle.ecommerce.cart.api.MergeGuestCartOnLogin;
import com.phucle.ecommerce.cart.domain.Cart;
import com.phucle.ecommerce.cart.domain.CartFailure;
import com.phucle.ecommerce.cart.domain.CartLine;
import com.phucle.ecommerce.cart.domain.Quantity;
import com.phucle.ecommerce.catalog.api.CartOfferReadPort;
import com.phucle.ecommerce.inventory.api.PublicStockReadPort;
import com.phucle.ecommerce.identity.api.TrustedActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartCommandService implements CartCommands, MergeGuestCartOnLogin {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final CartRepositoryPort repository;
    private final GuestCartProjection queries;
    private final CartOfferReadPort offers;
    private final PublicStockReadPort stocks;
    private final GuestSessionPolicy settings;
    public CartCommandService(CartRepositoryPort repository,GuestCartProjection queries,CartOfferReadPort offers,
            PublicStockReadPort stocks,GuestSessionPolicy settings) {
        this.repository=repository;this.queries=queries;this.offers=offers;this.stocks=stocks;this.settings=settings;
    }
    @Transactional
    @Override public GuestCart getOrCreateGuest(String proof) {
        CartView existing=queries.existingGuest(proof==null?null:hash(proof));
        if (existing!=null) return new GuestCart(existing,null);
        String created=newProof();
        Cart cart=repository.createGuest(hash(created),Instant.now().plusSeconds(settings.ttlSeconds()));
        return new GuestCart(view(cart),created);
    }
    @Transactional
    @Override public CartView setLine(TrustedActor actor,String proof,UUID variant,int quantity,long expected) {
        Cart cart=owned(actor,proof,true);
        Quantity requested=new Quantity(quantity);
        cart.requireVersion(expected);
        requireAvailable(variant,requested);
        long before=cart.version();cart.setQuantity(variant,requested);repository.save(cart,before);
        return view(cart);
    }
    @Transactional
    @Override public void removeLine(TrustedActor actor,String proof,UUID variant,long expected) {
        Cart cart=owned(actor,proof,false);
        if (expected<0) throw new IllegalArgumentException();
        cart.requireVersion(expected);
        long before=cart.version();cart.remove(variant);repository.save(cart,before);
    }
    @Override @Transactional
    public GuestCartMergeResult merge(GuestCartMergeCommand command) {
        requireMergeMember(command);
        Cart source=repository.guest(hash(command.guestSessionProof()),true);
        if (source==null) return new GuestCartMergeResult(false,false,List.of());
        if (source.mergedInto()!=null) return new GuestCartMergeResult(false,true,List.of());
        Cart target=memberCart(command.actor().accountId());
        long before=target.version();
        List<GuestCartMergeResult.RejectedGuestCartLine> rejected=mergeLines(source,target);
        if (target.version()!=before) repository.save(target,before);
        source.consumeInto(target.id());repository.save(source,source.version());
        return new GuestCartMergeResult(true,true,List.copyOf(rejected));
    }

    private void requireAvailable(UUID variant, Quantity requested) {
        var offer=offers.offers(Set.of(variant)).get(variant);
        var stock=stocks.findStock(Set.of(variant)).get(variant);
        if (offer==null || !offer.purchasable() || stock==null || requested.value()>stock.onHand())
            throw new CartFailure(CartFailure.Code.ITEM_UNAVAILABLE);
    }

    private static void requireMergeMember(GuestCartMergeCommand command) {
        if (command==null || command.actor()==null || !"MEMBER".equals(command.actor().operationalRole()))
            throw new CartFailure(CartFailure.Code.ROLE_DENIED);
    }

    private Cart memberCart(UUID member) {
        Cart target=repository.member(member,true);
        return target==null?repository.createMember(member):target;
    }

    private List<GuestCartMergeResult.RejectedGuestCartLine> mergeLines(Cart source,Cart target) {
        Set<UUID> ids=source.lines().stream().map(l->l.variantId()).collect(java.util.stream.Collectors.toSet());
        Map<UUID,CartOfferReadPort.Offer> currentOffers=offers.offers(ids);
        Map<UUID,PublicStockReadPort.Stock> currentStock=stocks.findStock(ids);
        List<GuestCartMergeResult.RejectedGuestCartLine> rejected=new ArrayList<>();
        for (var line:source.lines()) {
            String rejection=mergeLine(target,line,currentOffers.get(line.variantId()),currentStock.get(line.variantId()));
            if (rejection!=null) rejected.add(new GuestCartMergeResult.RejectedGuestCartLine(line.variantId(),rejection));
        }
        return rejected;
    }

    private String mergeLine(Cart target,CartLine line,
            CartOfferReadPort.Offer offer,PublicStockReadPort.Stock stock) {
        if (offer==null || !offer.purchasable() || stock==null) return "ITEM_UNAVAILABLE";
        try { target.mergeLine(line.variantId(),line.quantity(),stock.onHand()); }
        catch (CartFailure e) {
            if (e.code()!=CartFailure.Code.QUANTITY_EXCEEDED) throw e;
            return "QUANTITY_EXCEEDED";
        }
        return null;
    }

    private static String newProof() {
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private Cart owned(TrustedActor actor,String proof,boolean createMember) {
        if (actor != null) CartActorAuthorization.requireMember(actor);
        UUID member = actor == null ? null : actor.accountId();
        Cart cart=member==null?repository.guest(proof==null?null:hash(proof),true):repository.member(member,true);
        if (cart==null && member!=null && createMember) cart=repository.createMember(member);
        if (cart==null) throw new CartFailure(member==null?CartFailure.Code.AUTHENTICATION_REQUIRED:
                CartFailure.Code.RESOURCE_NOT_FOUND);
        if (cart.mergedInto()!=null) throw new CartFailure(CartFailure.Code.AUTHENTICATION_REQUIRED);
        return cart;
    }
    private String hash(String proof) {
        if (proof==null || proof.isBlank()) return null;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(proof.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private CartView view(Cart cart) {
        Set<UUID> ids=cart.lines().stream().map(l->l.variantId()).collect(java.util.stream.Collectors.toSet());
        var current=offers.offers(ids);
        List<CartView.Line> lines=new ArrayList<>(); java.math.BigDecimal total=java.math.BigDecimal.ZERO;
        for (var line:cart.lines()) {
            var offer=current.get(line.variantId());
            if (offer==null) throw new IllegalStateException("Stored cart variant is missing");
            lines.add(new CartView.Line(line.variantId(),line.quantity().value(),offer.priceUsd().toPlainString()));
            total=total.add(offer.priceUsd().multiply(java.math.BigDecimal.valueOf(line.quantity().value())));
        }
        return new CartView(lines,total.setScale(2).toPlainString(),cart.version());
    }
}

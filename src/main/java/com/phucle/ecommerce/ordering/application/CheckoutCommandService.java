package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.identity.api.OwnedAddressQueries;
import com.phucle.ecommerce.cart.api.CheckoutCart;
import com.phucle.ecommerce.catalog.api.CheckoutOffers;
import com.phucle.ecommerce.inventory.api.CheckoutStock;
import com.phucle.ecommerce.audit.api.OrderAudit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.phucle.ecommerce.ordering.domain.OrderFactory;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import com.phucle.ecommerce.ordering.domain.CustomerOrder;
import com.phucle.ecommerce.ordering.domain.OrderPlacementLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
public class CheckoutCommandService implements PlaceCodOrder {
    private final OrderWritePort writes;
    private final CreationSnapshotReadPort reads;
    private final CheckoutCart carts;
    private final OwnedAddressQueries addresses;
    private final CheckoutOffers offers;
    private final CheckoutStock stocks;
    private final OrderAudit audit;
    public CheckoutCommandService(OrderWritePort writes, CreationSnapshotReadPort reads, CheckoutCart carts,
            OwnedAddressQueries addresses, CheckoutOffers offers, CheckoutStock stocks, OrderAudit audit) {
        this.writes = writes; this.reads = reads; this.carts = carts; this.addresses = addresses;
        this.offers = offers; this.stocks = stocks; this.audit = audit;
    }
    @Override
    @Transactional
    public CreationSnapshotReadPort.Order execute(TrustedActor actor, Command command) {
        if (actor == null || !"MEMBER".equals(actor.operationalRole())) throw new OrderFailure("ROLE_DENIED");
        if (command == null) throw new IllegalArgumentException("Invalid checkout request");
        UUID addressId = command.addressId();
        long cartVersion = command.cartVersion();
        String method = command.paymentMethod();
        String voucherCode = command.voucherCode();
        String key = command.idempotencyKey();
        if (addressId == null || cartVersion < 0 || !"COD".equals(method) || key == null
                || key.isBlank() || key.length() > 128 || voucherCode != null && (voucherCode.isBlank() || voucherCode.length() > 100))
            throw new IllegalArgumentException("Invalid checkout request");
        UUID memberId = actor.accountId();
        String fingerprint = fingerprint(addressId, cartVersion, voucherCode);
        writes.serializeIntent(memberId, key);
        var existing = writes.findExisting(memberId, key);
        if (existing.isPresent()) {
            if (!fingerprint.equals(existing.get().fingerprint())) throw new OrderFailure("IDEMPOTENCY_CONFLICT");
            return reads.readCreation(existing.get().id(), voucherCode);
        }
        var cart = carts.lockOwned(memberId);
        if (cart == null || cart.version() != cartVersion) throw new OrderFailure("CART_CHANGED");
        var ownedAddress = addresses.findOwned(actor, addressId);
        var address = new AddressSnapshot(ownedAddress.recipient(), ownedAddress.addressLine(),
                ownedAddress.locality(), ownedAddress.postalCode(), ownedAddress.country());
        if (voucherCode != null) throw new OrderFailure("VOUCHER_INVALID");
        if (cart.lines().isEmpty()) throw new OrderFailure("EMPTY_CART");
        List<UUID> ids = cart.lines().stream().map(CheckoutCart.Line::variantId).toList();
        Map<UUID, CheckoutOffers.Offer> current = offers.lockCurrent(ids).stream()
                .collect(Collectors.toMap(CheckoutOffers.Offer::variantId, Function.identity()));
        if (current.size() != ids.size() || current.values().stream().anyMatch(offer -> !offer.purchasable()))
            throw new OrderFailure("ITEM_UNAVAILABLE");
        List<OrderPlacementLine> lines = cart.lines().stream().map(line -> {
            var offer = current.get(line.variantId());
            return new OrderPlacementLine(offer.productId(), offer.sellerShopId(), offer.variantId(), offer.title(),
                    offer.sku(), line.quantity(), offer.unitPriceUsd(), new BigDecimal("0.00"));
        }).toList();
        CustomerOrder order = OrderFactory.place(memberId, address, lines);
        Map<UUID, Integer> quantities = cart.lines().stream()
                .collect(Collectors.toMap(CheckoutCart.Line::variantId, CheckoutCart.Line::quantity));
        if (!stocks.decrease(quantities)) throw new OrderFailure("STOCK_UNAVAILABLE");
        writes.save(key, fingerprint, order);
        carts.consume(cart.id(), cartVersion);
        audit.placed(memberId, order.id());
        return reads.readCreation(order.id(), voucherCode);
    }

    private static String fingerprint(UUID addressId, long version, String voucher) {
        try {
            String canonical = addressId + "|" + version + "|COD|" + (voucher == null ? "-" : voucher.length() + ":" + voucher);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

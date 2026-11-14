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
import java.util.Optional;
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
        validate(command);
        UUID memberId = actor.accountId();
        String fingerprint = fingerprint(command.addressId(), command.cartVersion(), command.voucherCode());
        writes.serializeIntent(memberId, command.idempotencyKey());
        var existing = existingPlacement(memberId, command.idempotencyKey(), fingerprint);
        if (existing.isPresent()) return reads.readCreation(existing.get().id(), command.voucherCode());

        var cart = lockCart(memberId, command.cartVersion());
        var address = snapshotAddress(actor, command.addressId());
        if (command.voucherCode() != null) throw new OrderFailure("VOUCHER_INVALID");
        CustomerOrder order = OrderFactory.place(memberId, address, placementLines(cart));
        reserveStock(cart);
        writes.save(command.idempotencyKey(), fingerprint, order);
        carts.consume(cart.id(), command.cartVersion());
        audit.placed(memberId, order.id());
        return reads.readCreation(order.id(), command.voucherCode());
    }

    private static void validate(Command command) {
        if (command == null || command.addressId() == null || command.cartVersion() < 0
                || !"COD".equals(command.paymentMethod()) || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 128
                || command.voucherCode() != null
                && (command.voucherCode().isBlank() || command.voucherCode().length() > 100))
            throw new IllegalArgumentException("Invalid checkout request");
    }

    private Optional<OrderWritePort.Existing> existingPlacement(UUID memberId, String key, String fingerprint) {
        var existing = writes.findExisting(memberId, key);
        if (existing.isPresent() && !fingerprint.equals(existing.get().fingerprint()))
            throw new OrderFailure("IDEMPOTENCY_CONFLICT");
        return existing;
    }

    private CheckoutCart.Locked lockCart(UUID memberId, long expectedVersion) {
        var cart = carts.lockOwned(memberId);
        if (cart == null || cart.version() != expectedVersion) throw new OrderFailure("CART_CHANGED");
        return cart;
    }

    private AddressSnapshot snapshotAddress(TrustedActor actor, UUID addressId) {
        var ownedAddress = addresses.findOwned(actor, addressId);
        return new AddressSnapshot(ownedAddress.recipient(), ownedAddress.addressLine(),
                ownedAddress.locality(), ownedAddress.postalCode(), ownedAddress.country());
    }

    private List<OrderPlacementLine> placementLines(CheckoutCart.Locked cart) {
        if (cart.lines().isEmpty()) throw new OrderFailure("EMPTY_CART");
        List<UUID> ids = cart.lines().stream().map(CheckoutCart.Line::variantId).toList();
        Map<UUID, CheckoutOffers.Offer> current = offers.lockCurrent(ids).stream()
                .collect(Collectors.toMap(CheckoutOffers.Offer::variantId, Function.identity()));
        if (current.size() != ids.size() || current.values().stream().anyMatch(offer -> !offer.purchasable()))
            throw new OrderFailure("ITEM_UNAVAILABLE");
        return cart.lines().stream().map(line -> {
            var offer = current.get(line.variantId());
            return new OrderPlacementLine(offer.productId(), offer.sellerShopId(), offer.variantId(), offer.title(),
                    offer.sku(), line.quantity(), offer.unitPriceUsd(), new BigDecimal("0.00"));
        }).toList();
    }

    private void reserveStock(CheckoutCart.Locked cart) {
        Map<UUID, Integer> quantities = cart.lines().stream()
                .collect(Collectors.toMap(CheckoutCart.Line::variantId, CheckoutCart.Line::quantity));
        if (!stocks.decrease(quantities)) throw new OrderFailure("STOCK_UNAVAILABLE");
    }

    private static String fingerprint(UUID addressId, long version, String voucher) {
        try {
            String canonical = addressId + "|" + version + "|COD|" + (voucher == null ? "-" : voucher.length() + ":" + voucher);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

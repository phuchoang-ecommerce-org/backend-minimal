package com.phucle.ecommerce.ordering.application;

import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Compile-only checkout command seam for the Sprint 1 RED contract. */
public final class PlaceOrder {
    public record CartIntent(long version, List<UUID> variantIds) {
        public CartIntent { variantIds = List.copyOf(variantIds); }
    }
    public record AddressSnapshot(String recipient, String addressLine, String locality, String postalCode, String country) {
    }
    public interface Orders {
        Optional<Result> findReplay(UUID memberId, String key, Command command);
        void save(UUID memberId, String key, Command command, Result result);
    }
    public interface Carts {
        CartIntent lockOwned(UUID memberId);
        void consume(UUID memberId, long expectedVersion);
    }
    public interface Addresses {
        AddressSnapshot findOwned(UUID memberId, UUID addressId);
    }
    public interface Offers {
        void requirePurchasable(List<UUID> variantIds);
    }
    public interface Stocks {
        void decrease(List<UUID> variantIds);
    }
    public interface Vouchers {
        void validate(String voucherCode);
    }
    public interface Audit {
        void append(UUID actorId, UUID orderId);
    }

    private final Orders orders;
    private final Carts carts;
    private final Addresses addresses;
    private final Offers offers;
    private final Stocks stocks;
    private final Vouchers vouchers;
    private final Audit audit;

    public PlaceOrder(Orders orders, Carts carts, Addresses addresses, Offers offers,
            Stocks stocks, Vouchers vouchers, Audit audit) {
        this.orders = orders;
        this.carts = carts;
        this.addresses = addresses;
        this.offers = offers;
        this.stocks = stocks;
        this.vouchers = vouchers;
        this.audit = audit;
    }

    public record Command(UUID addressId, long cartVersion, String paymentMethod,
                          String voucherCode, String idempotencyKey) {
    }

    public record Result(UUID orderId, String status) {
    }

    public Result execute(TrustedActor actor, Command command) {
        throw new UnsupportedOperationException("PB-006 checkout awaits Sprint 2 implementation");
    }
}

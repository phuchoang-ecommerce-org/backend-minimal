package com.phucle.ecommerce.ordering.domain;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public final class CustomerOrder {
    public enum Status { PLACED, CANCELLED }
    public static final class CancellationBlocked extends IllegalStateException {
        public CancellationBlocked() { super("Paid portion prevents cancellation"); }
    }
    @Identity private final UUID id;
    private final UUID memberId;
    private final AddressSnapshot address;
    private final List<SellerOrder> portions;
    private final MoneyUsd total;
    private Status status;
    private Instant cancelledAt;
    CustomerOrder(UUID id, UUID memberId, AddressSnapshot address, List<SellerOrder> portions) {
        this(id, memberId, address, portions, Status.PLACED, null);
    }
    private CustomerOrder(UUID id, UUID memberId, AddressSnapshot address, List<SellerOrder> portions, Status status, Instant cancelledAt) {
        this.id = Objects.requireNonNull(id); this.memberId = Objects.requireNonNull(memberId);
        this.address = Objects.requireNonNull(address); this.portions = List.copyOf(portions);
        if (this.portions.isEmpty() || this.portions.stream().map(SellerOrder::sellerId).distinct().count() != this.portions.size())
            throw new IllegalArgumentException("Invalid seller portions");
        this.total = this.portions.stream().map(SellerOrder::total).reduce(MoneyUsd.zero(), MoneyUsd::add);
        this.status = Objects.requireNonNull(status);
        if ((status == Status.CANCELLED) != (cancelledAt != null)) throw new IllegalArgumentException("Invalid cancellation state");
        if (status == Status.CANCELLED && this.portions.stream().anyMatch(p -> "PAID".equals(p.paymentStatus())))
            throw new OrderStateCorruption("Cancelled order contains a paid portion");
        this.cancelledAt = cancelledAt;
    }
    static CustomerOrder placed(UUID memberId, AddressSnapshot address, List<SellerOrder> portions) {
        return new CustomerOrder(UUID.randomUUID(), memberId, address, portions);
    }
    public static CustomerOrder rehydrate(UUID id, UUID memberId, AddressSnapshot address, List<SellerOrder> portions,
                                           String status, Instant cancelledAt, BigDecimal storedTotalUsd) {
        CustomerOrder order = new CustomerOrder(id, memberId, address, portions, Status.valueOf(status), cancelledAt);
        if (storedTotalUsd == null || order.totalUsd().compareTo(storedTotalUsd) != 0)
            throw new OrderStateCorruption("Stored customer order total disagrees with portions");
        return order;
    }
    public boolean cancel(Instant when) {
        Objects.requireNonNull(when);
        if (status == Status.CANCELLED) return false;
        if (portions.stream().anyMatch(p -> !"PENDING_PAYMENT".equals(p.paymentStatus())))
            throw new CancellationBlocked();
        status = Status.CANCELLED; cancelledAt = when; return true;
    }
    public void recordCod(UUID sellerOrderId, UUID sellerId, Instant when) {
        Objects.requireNonNull(sellerOrderId); Objects.requireNonNull(sellerId); Objects.requireNonNull(when);
        if (status != Status.PLACED) throw new IllegalStateException("Cancelled order cannot be paid");
        SellerOrder portion = portions.stream().filter(p -> p.id().equals(sellerOrderId) && p.sellerId().equals(sellerId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Seller portion not found"));
        portion.markPaid(when);
    }
    public UUID id() { return id; }
    public UUID memberId() { return memberId; }
    public String status() { return status.name(); }
    public String paymentStatus() {
        return portions.stream().allMatch(p -> "PAID".equals(p.paymentStatus())) ? "PAID" : "PENDING_PAYMENT";
    }
    public AddressSnapshot address() { return address; }
    public List<SellerOrder> portions() { return portions; }
    public java.math.BigDecimal totalUsd() { return total.amount(); }
    public Instant cancelledAt() { return cancelledAt; }
}

package com.phucle.ecommerce.ordering.domain;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

@Entity
public final class SellerOrder {
    public enum PaymentStatus { PENDING_PAYMENT, PAID }
    @Identity private final UUID id;
    private final UUID sellerId;
    private final List<OrderLine> lines;
    private final MoneyUsd total;
    private PaymentStatus paymentStatus;
    private Instant paidAt;
    SellerOrder(UUID id, UUID sellerId, List<OrderLine> lines) {
        this(id, sellerId, lines, PaymentStatus.PENDING_PAYMENT, null);
    }
    private SellerOrder(UUID id, UUID sellerId, List<OrderLine> lines, PaymentStatus paymentStatus, Instant paidAt) {
        this.id = Objects.requireNonNull(id); this.sellerId = Objects.requireNonNull(sellerId);
        this.lines = List.copyOf(lines);
        if (this.lines.isEmpty() || this.lines.stream().anyMatch(line -> !line.snapshot().sellerId().equals(sellerId)))
            throw new IllegalArgumentException("Invalid seller portion lines");
        this.total = this.lines.stream().map(OrderLine::total).reduce(MoneyUsd.zero(), MoneyUsd::add);
        this.paymentStatus = Objects.requireNonNull(paymentStatus);
        if ((paymentStatus == PaymentStatus.PAID) != (paidAt != null)) throw new IllegalArgumentException("Invalid payment state");
        this.paidAt = paidAt;
    }
    static SellerOrder pending(UUID sellerId, List<OrderLine> lines) {
        return new SellerOrder(UUID.randomUUID(), sellerId, lines);
    }
    public static SellerOrder rehydrate(UUID id, UUID sellerId, List<OrderLine> lines, String paymentStatus,
                                        Instant paidAt, BigDecimal storedTotalUsd) {
        SellerOrder portion = new SellerOrder(id, sellerId, lines, PaymentStatus.valueOf(paymentStatus), paidAt);
        if (storedTotalUsd == null || portion.totalUsd().compareTo(storedTotalUsd) != 0)
            throw new OrderStateCorruption("Stored seller portion total disagrees with lines");
        return portion;
    }
    void markPaid(Instant when) {
        Objects.requireNonNull(when);
        if (paymentStatus != PaymentStatus.PENDING_PAYMENT) throw new IllegalStateException("Portion already paid");
        paymentStatus = PaymentStatus.PAID; paidAt = when;
    }
    public UUID id() { return id; }
    public UUID sellerId() { return sellerId; }
    public List<OrderLine> lines() { return lines; }
    public String paymentStatus() { return paymentStatus.name(); }
    public MoneyUsd total() { return total; }
    public java.math.BigDecimal totalUsd() { return total.amount(); }
    public Instant paidAt() { return paidAt; }
}

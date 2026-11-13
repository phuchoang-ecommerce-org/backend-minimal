package com.phucle.ecommerce.ordering.domain;

import java.time.Instant;
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
        this.id = Objects.requireNonNull(id); this.sellerId = Objects.requireNonNull(sellerId);
        this.lines = List.copyOf(lines);
        if (this.lines.isEmpty() || this.lines.stream().anyMatch(line -> !line.snapshot().sellerId().equals(sellerId)))
            throw new IllegalArgumentException("Invalid seller portion lines");
        this.total = this.lines.stream().map(OrderLine::total).reduce(MoneyUsd.zero(), MoneyUsd::add);
        this.paymentStatus = PaymentStatus.PENDING_PAYMENT;
    }
    static SellerOrder pending(UUID sellerId, List<OrderLine> lines) {
        return new SellerOrder(UUID.randomUUID(), sellerId, lines);
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

package com.phucle.ecommerce.ordering.domain;

import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;

@Entity
public final class OrderLine {
    @Identity private final UUID id;
    private final PurchasedLineSnapshot snapshot;
    OrderLine(UUID id, PurchasedLineSnapshot snapshot) {
        this.id = Objects.requireNonNull(id); this.snapshot = Objects.requireNonNull(snapshot);
    }
    static OrderLine create(PurchasedLineSnapshot snapshot) { return new OrderLine(UUID.randomUUID(), snapshot); }
    public static OrderLine rehydrate(UUID id, PurchasedLineSnapshot snapshot, BigDecimal storedTotalUsd) {
        OrderLine line = new OrderLine(id, snapshot);
        if (storedTotalUsd == null || line.total().amount().compareTo(storedTotalUsd) != 0)
            throw new OrderStateCorruption("Stored order line total disagrees with its snapshot");
        return line;
    }
    public UUID id() { return id; }
    public PurchasedLineSnapshot snapshot() { return snapshot; }
    public MoneyUsd total() { return snapshot.total(); }
}

package com.phucle.ecommerce.ordering.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record MoneyUsd(BigDecimal amount) {
    public MoneyUsd {
        amount = Objects.requireNonNull(amount).setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() < 0 || amount.precision() - amount.scale() > 10)
            throw new IllegalArgumentException("Invalid USD amount");
    }
    public MoneyUsd add(MoneyUsd other) { return new MoneyUsd(amount.add(other.amount)); }
    public MoneyUsd subtract(MoneyUsd other) { return new MoneyUsd(amount.subtract(other.amount)); }
    public static MoneyUsd zero() { return new MoneyUsd(new BigDecimal("0.00")); }
}

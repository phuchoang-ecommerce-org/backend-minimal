package com.phucle.ecommerce.identity.domain;

import java.util.UUID;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record Address(@Identity AddressId id, MemberId memberId, AddressValue value) {
    public Address {
        if (id == null || memberId == null || value == null) throw new IllegalArgumentException("Invalid address");
    }
    public static Address create(MemberId memberId, AddressValue value) {
        return new Address(new AddressId(UUID.randomUUID()), memberId, value);
    }
}

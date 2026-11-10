package com.phucle.ecommerce.identity.domain;

import java.util.Objects;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public final class Account {
    @Identity private final AccountId id;
    private final NormalizedLogin login;
    private final String passwordHash;
    private final String displayName;
    private final OperationalRole role;
    private final AccountStatus status;

    private Account(AccountId id, NormalizedLogin login, String passwordHash, String displayName,
                    OperationalRole role, AccountStatus status) {
        this.id = Objects.requireNonNull(id);
        this.login = Objects.requireNonNull(login);
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("passwordHash");
        this.passwordHash = passwordHash;
        if (displayName == null || displayName.isBlank() || displayName.length() > 100)
            throw new IllegalArgumentException("displayName");
        this.displayName = displayName.trim();
        this.role = Objects.requireNonNull(role);
        this.status = Objects.requireNonNull(status);
    }

    public static Account register(AccountId id, NormalizedLogin login, String passwordHash, String displayName) {
        return new Account(id, login, passwordHash, displayName, OperationalRole.MEMBER, AccountStatus.ACTIVE);
    }

    public static Account restore(AccountId id, NormalizedLogin login, String passwordHash, String displayName,
                                  OperationalRole role, AccountStatus status) {
        return new Account(id, login, passwordHash, displayName, role, status);
    }

    public AccountId id() { return id; }
    public NormalizedLogin login() { return login; }
    public String passwordHash() { return passwordHash; }
    public String displayName() { return displayName; }
    public OperationalRole role() { return role; }
    public AccountStatus status() { return status; }
    public boolean active() { return AccountStatus.ACTIVE.equals(status); }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}

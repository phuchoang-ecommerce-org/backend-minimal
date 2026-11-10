package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.domain.Account;
import com.phucle.ecommerce.identity.domain.AccountId;
import com.phucle.ecommerce.identity.domain.NormalizedLogin;
import java.util.Optional;

public interface IdentityAccountRepository {

    void insert(Account account);

    Optional<Account> findByNormalizedLogin(NormalizedLogin login);

    Optional<Account> findById(AccountId id);
}

package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.api.AccountView;
import com.phucle.ecommerce.identity.api.InvalidCredentialsException;
import com.phucle.ecommerce.identity.api.LogIn;
import com.phucle.ecommerce.identity.api.LoginCommand;
import com.phucle.ecommerce.identity.api.LoginResult;
import com.phucle.ecommerce.identity.api.RegisterMember;
import com.phucle.ecommerce.identity.api.RegisterMemberCommand;
import com.phucle.ecommerce.identity.domain.Account;
import com.phucle.ecommerce.identity.domain.AccountId;
import com.phucle.ecommerce.identity.domain.NormalizedLogin;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityApplicationService implements RegisterMember, LogIn {

    private final IdentityAccountRepository accounts;
    private final CredentialVerifier credentials;
    private final AccessTokenIssuer accessTokens;

    IdentityApplicationService(
            IdentityAccountRepository accounts, CredentialVerifier credentials, AccessTokenIssuer accessTokens) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.accessTokens = accessTokens;
    }

    @Override
    @Transactional
    public AccountView register(RegisterMemberCommand command) {
        Account account = Account.register(new AccountId(UUID.randomUUID()), new NormalizedLogin(command.login()),
                credentials.hash(command.password()), command.displayName());
        accounts.insert(account);
        return view(account);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult logIn(LoginCommand command) {
        Account account = accounts.findByNormalizedLogin(new NormalizedLogin(command.login()))
                .orElseThrow(InvalidCredentialsException::new);
        if (!account.active() || !credentials.matches(command.password(), account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        IssuedAccessToken token = accessTokens.issue(account.id().value(), account.role().name());
        return new LoginResult(
                token.value(),
                token.expiresInSeconds(),
                view(account));
    }

    private AccountView view(Account account) {
        return new AccountView(account.id().value(), account.login().value(), account.displayName(), account.role().name());
    }
}

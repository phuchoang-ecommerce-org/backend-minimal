package com.phucle.ecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phucle.ecommerce.identity.domain.Account;
import com.phucle.ecommerce.identity.domain.AccountId;
import com.phucle.ecommerce.identity.domain.NormalizedLogin;
import com.phucle.ecommerce.identity.domain.OperationalRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountDomainTest {
    @Test
    void registrationEnforcesMemberRoleAndNormalizesLoginWithoutHttp() {
        Account account = Account.register(new AccountId(UUID.randomUUID()),
                new NormalizedLogin("  MEMBER@EXAMPLE.TEST  "), "bcrypt-hash", " Member ");
        assertThat(account.login().value()).isEqualTo("member@example.test");
        assertThat(account.role()).isEqualTo(OperationalRole.MEMBER);
        assertThat(account.active()).isTrue();
        assertThat(account.displayName()).isEqualTo("Member");
    }

    @Test
    void rejectsInvalidIdentityAndDisplayName() {
        assertThatThrownBy(() -> new AccountId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NormalizedLogin("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Account.register(new AccountId(UUID.randomUUID()),
                new NormalizedLogin("member@example.test"), "hash", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

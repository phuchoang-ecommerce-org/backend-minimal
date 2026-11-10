package com.phucle.ecommerce.identity.adapter.out.jdbc;

import com.phucle.ecommerce.identity.application.IdentityAccountRepository;
import com.phucle.ecommerce.identity.domain.Account;
import com.phucle.ecommerce.identity.domain.AccountId;
import com.phucle.ecommerce.identity.domain.AccountStatus;
import com.phucle.ecommerce.identity.domain.NormalizedLogin;
import com.phucle.ecommerce.identity.domain.OperationalRole;
import com.phucle.ecommerce.identity.api.LoginAlreadyExistsException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdentityAccountRepository implements IdentityAccountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcIdentityAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Account account) {
        try {
            jdbc.update("""
                    INSERT INTO marketplace.account (id, normalized_login, password_hash, display_name, role, status)
                    VALUES (:id, :normalizedLogin, :passwordHash, :displayName, :role, :status)
                    """, Map.of(
                    "id", account.id().value(),
                    "normalizedLogin", account.login().value(),
                    "passwordHash", account.passwordHash(),
                    "displayName", account.displayName(),
                    "role", account.role().name(),
                    "status", account.status().value()));
        } catch (DuplicateKeyException exception) {
            // This insert controls the UUID and has one other unique value: normalized_login.
            throw new LoginAlreadyExistsException();
        }
    }

    @Override
    public Optional<Account> findByNormalizedLogin(NormalizedLogin login) {
        return jdbc.query("""
                        SELECT id, normalized_login, password_hash, display_name, role, status
                        FROM marketplace.account
                        WHERE normalized_login = :normalizedLogin
                        """, Map.of("normalizedLogin", login.value()), (resultSet, rowNumber) -> map(resultSet))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.query("""
                        SELECT id, normalized_login, password_hash, display_name, role, status
                        FROM marketplace.account WHERE id = :id
                        """, Map.of("id", id.value()), (row, number) -> map(row))
                .stream().findFirst();
    }

    private Account map(java.sql.ResultSet row) throws java.sql.SQLException {
        return Account.restore(new AccountId(row.getObject("id", UUID.class)),
                new NormalizedLogin(row.getString("normalized_login")), row.getString("password_hash"),
                row.getString("display_name"), OperationalRole.valueOf(row.getString("role")),
                new AccountStatus(row.getString("status")));
    }
}

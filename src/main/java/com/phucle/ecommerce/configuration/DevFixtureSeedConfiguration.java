package com.phucle.ecommerce.configuration;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "marketplace.dev-fixtures", name = "enabled", havingValue = "true")
class DevFixtureSeedConfiguration {

    static final UUID MEMBER_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SELLER_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID SELLER_SHOP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID ADMIN_ACCOUNT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Bean
    ApplicationRunner seedSyntheticDevFixtures(JdbcTemplate jdbc, DevFixtureProperties properties) {
        return arguments -> {
            insertAccount(jdbc, MEMBER_ACCOUNT_ID, "synthetic.member@example.test", properties.memberPasswordHash(), "MEMBER");
            insertAccount(jdbc, SELLER_ACCOUNT_ID, "synthetic.seller@example.test", properties.sellerPasswordHash(), "SELLER");
            insertAccount(jdbc, ADMIN_ACCOUNT_ID, "synthetic.admin@example.test", properties.adminPasswordHash(), "ADMIN");
            jdbc.update("""
                    INSERT INTO marketplace.seller_shop (id, account_id, name, status)
                    VALUES (?, ?, 'Synthetic Seller Shop', 'ACTIVE')
                    ON CONFLICT DO NOTHING
                    """, SELLER_SHOP_ID, SELLER_ACCOUNT_ID);
        };
    }

    private void insertAccount(JdbcTemplate jdbc, UUID id, String login, String passwordHash, String role) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalStateException("A local password hash is required when dev fixtures are enabled");
        }
        jdbc.update("""
                INSERT INTO marketplace.account (id, normalized_login, password_hash, role, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                ON CONFLICT DO NOTHING
                """, id, login, passwordHash, role);
    }
}

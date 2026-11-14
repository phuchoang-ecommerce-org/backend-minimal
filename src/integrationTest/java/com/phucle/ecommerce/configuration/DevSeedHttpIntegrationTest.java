package com.phucle.ecommerce.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@SpringBootTest(classes = MarketplaceApplication.class, properties = "marketplace.dev-fixtures.enabled=true")
class DevSeedHttpIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("seed_test").withPassword("seed_test");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_RUNTIME_USER", POSTGRES::getUsername);
        registry.add("DB_RUNTIME_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_MIGRATION_USER", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_PRIVATE_KEY", JwtTestKeys::privateKey);
        registry.add("JWT_PUBLIC_KEY", JwtTestKeys::publicKey);
        registry.add("CURSOR_MAC_SECRET", () -> "test-only-cursor-mac-secret-at-least-32-bytes");
    }

    @Test void seedsAndRefreshesOnlySyntheticAccounts() throws Exception {
        seed("FirstMember2026!", "FirstSeller2026!", "FirstAdmin2026!");
        seed("SecondMember2026!", "SecondSeller2026!", "SecondAdmin2026!");
        String hash = jdbc.queryForObject("SELECT password_hash FROM marketplace.account WHERE id=?", String.class,
                DevFixtureSeedConfiguration.MEMBER_ACCOUNT_ID);
        assertThat(passwords.matches("SecondMember2026!", hash)).isTrue();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"synthetic.member@example.test\",\"password\":\"SecondMember2026!\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.account WHERE normalized_login LIKE 'synthetic.%'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT account_id FROM marketplace.seller_shop WHERE id=?", java.util.UUID.class,
                DevFixtureSeedConfiguration.SELLER_SHOP_ID)).isEqualTo(DevFixtureSeedConfiguration.SELLER_ACCOUNT_ID);
    }

    @Test void seedsCatalogAddressAndOrderHistoryIdempotently() throws Exception {
        String payload = """
                {"memberPassword":"CatalogMember2026!","sellerPassword":"CatalogSeller2026!",
                 "adminPassword":"CatalogAdmin2026!","data":{
                  "categories":[
                   {"id":"55555555-5555-5555-5555-555555555551","name":"Synthetic Books"},
                   {"id":"55555555-5555-5555-5555-555555555552","name":"Synthetic Home"}],
                  "products":[
                   {"id":"66666666-6666-6666-6666-666666666661","categoryId":"55555555-5555-5555-5555-555555555551","variantId":"77777777-7777-7777-7777-777777777771","title":"Synthetic Book","sku":"SYNTHETIC-BOOK","priceUsd":12.50,"stock":7,
                    "image":{"id":"cccccccc-cccc-cccc-cccc-ccccccccccc1","url":"https://upload.wikimedia.org/wikipedia/commons/1/14/Book_still_photography.jpg","sourceName":"Wikimedia Commons: https://commons.wikimedia.org/wiki/File:Book_still_photography.jpg","license":"https://creativecommons.org/publicdomain/zero/1.0/","altText":"Books arranged on a table"}},
                   {"id":"66666666-6666-6666-6666-666666666662","categoryId":"55555555-5555-5555-5555-555555555552","variantId":"77777777-7777-7777-7777-777777777772","title":"Synthetic Lamp","sku":"SYNTHETIC-LAMP","priceUsd":21.00,"stock":9}],
                  "addresses":[{"id":"88888888-8888-8888-8888-888888888881","recipient":"Synthetic Member","addressLine":"10 Example Street","locality":"Test City","postalCode":"10000","country":"US"}],
                  "orders":[
                   {"id":"99999999-9999-9999-9999-999999999991","sellerOrderId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1","lineId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1","variantId":"77777777-7777-7777-7777-777777777771","quantity":2,"paymentStatus":"PAID"},
                   {"id":"99999999-9999-9999-9999-999999999992","sellerOrderId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2","lineId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2","variantId":"77777777-7777-7777-7777-777777777772","quantity":1,"paymentStatus":"PENDING_PAYMENT"}],
                  "reviews":[]}}
                """;
        mvc.perform(post("/dev/seed").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNoContent());
        mvc.perform(post("/dev/seed").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.category WHERE name LIKE 'Synthetic %'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.product WHERE seller_id=?", Integer.class,
                DevFixtureSeedConfiguration.SELLER_SHOP_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.product_image WHERE product_id=? AND mode='LINK' AND source_url=? AND source_name=? AND license=? AND sort_order=0 AND alt_text=?", Integer.class,
                java.util.UUID.fromString("66666666-6666-6666-6666-666666666661"),
                "https://upload.wikimedia.org/wikipedia/commons/1/14/Book_still_photography.jpg",
                "Wikimedia Commons: https://commons.wikimedia.org/wiki/File:Book_still_photography.jpg",
                "https://creativecommons.org/publicdomain/zero/1.0/", "Books arranged on a table")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.address WHERE member_id=?", Integer.class,
                DevFixtureSeedConfiguration.MEMBER_ACCOUNT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.customer_order WHERE member_id=?", Integer.class,
                DevFixtureSeedConfiguration.MEMBER_ACCOUNT_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.seller_order WHERE seller_id=? AND payment_status='PAID'", Integer.class,
                DevFixtureSeedConfiguration.SELLER_SHOP_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.order_line WHERE sku_snapshot LIKE 'SYNTHETIC-%'", Integer.class)).isEqualTo(2);
    }

    @Test void rejectsNonLoopbackCallerWithoutWrites() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM marketplace.account", Integer.class);
        mvc.perform(post("/dev/seed").with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(body("A-password-2026!", "B-password-2026!", "C-password-2026!")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.account", Integer.class)).isEqualTo(before);
    }

    @Test void rollsBackPasswordRefreshOnFixtureIdentityCollision() throws Exception {
        seed("BeforeMember2026!", "BeforeSeller2026!", "BeforeAdmin2026!");
        jdbc.update("UPDATE marketplace.account SET normalized_login=? WHERE id=?",
                "synthetic.conflict@example.test", DevFixtureSeedConfiguration.ADMIN_ACCOUNT_ID);
        try {
            mvc.perform(post("/dev/seed").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("AfterMember2026!", "AfterSeller2026!", "AfterAdmin2026!")))
                    .andExpect(status().isConflict());
            String hash = jdbc.queryForObject("SELECT password_hash FROM marketplace.account WHERE id=?", String.class,
                    DevFixtureSeedConfiguration.MEMBER_ACCOUNT_ID);
            assertThat(passwords.matches("BeforeMember2026!", hash)).isTrue();
        } finally {
            jdbc.update("UPDATE marketplace.account SET normalized_login=? WHERE id=?",
                    "synthetic.admin@example.test", DevFixtureSeedConfiguration.ADMIN_ACCOUNT_ID);
        }
    }

    private void seed(String member, String seller, String admin) throws Exception {
        mvc.perform(post("/dev/seed").with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(body(member, seller, admin)))
                .andExpect(status().isNoContent());
    }

    private String body(String member, String seller, String admin) {
        return "{\"memberPassword\":\"" + member + "\",\"sellerPassword\":\"" + seller
                + "\",\"adminPassword\":\"" + admin + "\"}";
    }
}

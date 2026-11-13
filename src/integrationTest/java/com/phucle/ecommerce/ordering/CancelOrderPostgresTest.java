package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@SpringBootTest(classes = MarketplaceApplication.class)
class CancelOrderPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("cancel_order").withPassword("cancel_order");
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
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired DataSource dataSource;

    @Test void cancelRestoresOnceAndReplayHasNoEffects() throws Exception {
        Fixture f = fixture();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.paymentStatus").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.sellerOrders[0].status").value("CANCELLED"))
                    .andExpect(jsonPath("$.sellerOrders[0].paymentStatus").value("PENDING_PAYMENT"));
        }
        assertThat(stock(f.firstVariant())).isEqualTo(5);
        assertThat(stock(f.secondVariant())).isEqualTo(5);
        assertThat(version(f.firstVariant())).isEqualTo(2);
        assertThat(version(f.secondVariant())).isEqualTo(2);
        assertThat(audits(f.order())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.seller_order WHERE customer_order_id=? AND payment_status='PAID'", Integer.class, f.order())).isZero();
    }

    @Test void foreignAndMissingAreHidden() throws Exception {
        Fixture f = fixture();
        mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.other(), "MEMBER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/orders/{orderId}/cancellation", UUID.randomUUID()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isNotFound());
        assertUnchanged(f);
    }

    @Test void paidPortionBlocksCancellation() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE marketplace.seller_order SET payment_status='PAID',paid_at=now() WHERE id=?", f.firstPortion());
        mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ILLEGAL_ORDER_STATE"));
        assertUnchanged(f);
    }

    @Test void corruptRootTotalIsRejectedBeforeMutation() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE marketplace.customer_order SET total_usd=99.99 WHERE id=?", f.order());
        mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertUnchanged(f);
    }

    @Test void corruptPortionTotalIsRejectedBeforeMutation() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE marketplace.seller_order SET total_usd=99.99 WHERE id=?", f.firstPortion());
        mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertUnchanged(f);
    }

    @Test void cancelledOrderWithPaidPortionIsRejected() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE marketplace.customer_order SET status='CANCELLED',cancelled_at=now() WHERE id=?", f.order());
        jdbc.update("UPDATE marketplace.seller_order SET payment_status='PAID',paid_at=now() WHERE id=?", f.firstPortion());
        mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(jdbc.queryForObject("SELECT status FROM marketplace.customer_order WHERE id=?", String.class, f.order())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT version FROM marketplace.customer_order WHERE id=?", Long.class, f.order())).isZero();
        assertThat(stock(f.firstVariant())).isEqualTo(3);
        assertThat(stock(f.secondVariant())).isEqualTo(4);
        assertThat(audits(f.order())).isZero();
    }

    @Test void auditFailureRollsBackEverything() throws Exception {
        Fixture f = fixture();
        jdbc.execute("ALTER TABLE marketplace.audit_entry ADD CONSTRAINT reject_cancel_audit CHECK (action <> 'ORDER_CANCELLED' OR resource_id <> '"
                + f.order() + "'::uuid)");
        try {
            mvc.perform(post("/orders/{orderId}/cancellation", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                    .andExpect(status().isInternalServerError());
            assertUnchanged(f);
        } finally {
            jdbc.execute("ALTER TABLE marketplace.audit_entry DROP CONSTRAINT reject_cancel_audit");
        }
    }

    @Test void paymentWinsRootLock() throws Exception {
        Fixture f = fixture();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var payment = pool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try (var lock = connection.prepareStatement("SELECT id FROM marketplace.customer_order WHERE id=? FOR UPDATE")) {
                        lock.setObject(1, f.order());
                        lock.executeQuery().close();
                    }
                    locked.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Payment barrier timed out");
                    try (var update = connection.prepareStatement("UPDATE marketplace.seller_order SET payment_status='PAID',paid_at=now() WHERE id=?")) {
                        update.setObject(1, f.firstPortion());
                        update.executeUpdate();
                    }
                    connection.commit();
                }
                return true;
            });
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var cancellation = pool.submit(() -> mvc.perform(post("/orders/{orderId}/cancellation", f.order())
                    .header("Authorization", token(f.member(), "MEMBER"))).andReturn().getResponse());
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' "
                            + "AND query LIKE '%marketplace.customer_order%' AND pid <> pg_backend_pid()", Integer.class) > 0;
                    if (waiting) break;
                    Thread.onSpinWait();
                }
                assertThat(waiting).as("Cancellation waited on customer_order root lock").isTrue();
            } finally {
                release.countDown();
            }
            assertThat(payment.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cancellation.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(409);
        }
        assertUnchanged(f);
    }

    @Test void requiresMember() throws Exception {
        Fixture f = fixture();
        mvc.perform(post("/orders/{orderId}/cancellation", f.order())).andExpect(status().isUnauthorized());
        mvc.perform(post("/orders/{orderId}/cancellation", f.order())
                .header("Authorization", token(f.sellerAccount(), "SELLER"))).andExpect(status().isForbidden());
        assertUnchanged(f);
    }

    private void assertUnchanged(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT status FROM marketplace.customer_order WHERE id=?", String.class, f.order())).isEqualTo("PLACED");
        assertThat(jdbc.queryForObject("SELECT version FROM marketplace.customer_order WHERE id=?", Long.class, f.order())).isZero();
        assertThat(stock(f.firstVariant())).isEqualTo(3);
        assertThat(stock(f.secondVariant())).isEqualTo(4);
        assertThat(version(f.firstVariant())).isEqualTo(1);
        assertThat(version(f.secondVariant())).isEqualTo(1);
        assertThat(audits(f.order())).isZero();
    }
    private int stock(UUID variant) { return jdbc.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?", Integer.class, variant); }
    private long version(UUID variant) { return jdbc.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?", Long.class, variant); }
    private int audits(UUID order) { return jdbc.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE resource_id=? AND action='ORDER_CANCELLED'", Integer.class, order); }

    private Fixture fixture() {
        UUID member = account("MEMBER"), other = account("MEMBER"), sellerAccount = account("SELLER");
        UUID category = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?,?)", category, "Cancellation fixture");
        UUID[] variants = new UUID[2];
        UUID[] portions = new UUID[2];
        UUID order = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.customer_order(id,member_id,idempotency_key,request_fingerprint,status,total_usd,currency) VALUES (?,?,?,?,'PLACED',37.50,'USD')",
                order, member, UUID.randomUUID().toString(), "fixture");
        jdbc.update("INSERT INTO marketplace.order_address_snapshot(customer_order_id,recipient,address_line,locality,postal_code,country) VALUES (?,'A','1 St','Hanoi','10000','VN')", order);
        for (int i = 0; i < 2; i++) {
            UUID shop = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID(), portion = UUID.randomUUID();
            jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')", shop,
                    i == 0 ? sellerAccount : account("SELLER"), "Shop " + i);
            jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,?,?,?,?,true)",
                    product, shop, category, "Title " + i, "Fixture");
            jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,?,?,12.50)",
                    variant, product, "CANCEL-" + variant, "Fixture");
            jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand,version) VALUES (?,?,1)", variant, i == 0 ? 3 : 4);
            jdbc.update("INSERT INTO marketplace.seller_order(id,customer_order_id,seller_id,payment_status,total_usd) VALUES (?,?,?,'PENDING_PAYMENT',?)", portion, order, shop,
                    i == 0 ? new java.math.BigDecimal("25.00") : new java.math.BigDecimal("12.50"));
            jdbc.update("INSERT INTO marketplace.order_line(id,seller_order_id,variant_id,product_id,product_title_snapshot,sku_snapshot,quantity,unit_price_usd,discount_usd,line_total_usd) VALUES (?,?,?,?,'Title','SKU',?,12.50,0,?)",
                    UUID.randomUUID(), portion, variant, product, i == 0 ? 2 : 1,
                    i == 0 ? new java.math.BigDecimal("25.00") : new java.math.BigDecimal("12.50"));
            variants[i] = variant; portions[i] = portion;
        }
        return new Fixture(member, other, sellerAccount, order, variants[0], variants[1], portions[0]);
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) VALUES (?,?,?,?,'ACTIVE','Fixture')",
                id, id + "@example.test", "fixture", role);
        return id;
    }
    private String token(UUID account, String role) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("marketplace").subject(account.toString())
                .audience(List.of("marketplace-api")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
    private record Fixture(UUID member, UUID other, UUID sellerAccount, UUID order,
                           UUID firstVariant, UUID secondVariant, UUID firstPortion) {}
}

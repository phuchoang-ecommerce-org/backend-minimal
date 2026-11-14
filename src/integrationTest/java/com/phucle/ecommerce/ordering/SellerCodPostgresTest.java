package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import com.phucle.ecommerce.shared.CursorCodec;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
class SellerCodPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("seller_cod").withPassword("seller_cod");
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
    @Autowired ObjectMapper json;
    @Autowired CursorCodec cursors;
    @Autowired DataSource dataSource;

    @Test void twoSellersMarkOwnPortionsAndAggregateBecomesPaid() throws Exception {
        Fixture f = fixture();
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA()).header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(f.portionA().toString()))
                .andExpect(jsonPath("$.sellerId").value(f.shopA().toString()))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.lines.length()").value(1));
        mvc.perform(get("/orders/{id}", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PENDING_PAYMENT"));
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionB()).header("Authorization", token(f.sellerB(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        mvc.perform(get("/orders/{id}", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='SELLER_ORDER_PAID' AND resource_id IN (?,?)",
                Integer.class, f.portionA(), f.portionB())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT actor_id FROM marketplace.audit_entry WHERE action='SELLER_ORDER_PAID' AND resource_id=?",
                UUID.class, f.portionA())).isEqualTo(f.sellerA());
        assertThat(jdbc.queryForObject("SELECT paid_at FROM marketplace.seller_order WHERE id=?", Instant.class, f.portionA())).isNotNull();
    }

    @Test void foreignMissingDuplicateAndCancelledAreRejected() throws Exception {
        Fixture f = fixture();
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionB()).header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/seller/orders/{id}/mark-paid", UUID.randomUUID()).header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA()).header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk());
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA()).header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ILLEGAL_ORDER_STATE"));
        Fixture cancelled = fixture();
        jdbc.update("UPDATE marketplace.customer_order SET status='CANCELLED',cancelled_at=now() WHERE id=?", cancelled.order());
        mvc.perform(post("/seller/orders/{id}/mark-paid", cancelled.portionA()).header("Authorization", token(cancelled.sellerA(), "SELLER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ILLEGAL_ORDER_STATE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='SELLER_ORDER_PAID' AND resource_id=?",
                Integer.class, f.portionA())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM marketplace.seller_order WHERE id=?", String.class, cancelled.portionA()))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test void suspendedSellerMayRecordExistingCash() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE marketplace.seller_shop SET status='SUSPENDED' WHERE id=?", f.shopA());
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA()).header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test void unprovisionedSellerGetsEmptyPageAndCannotMarkForeignPortion() throws Exception {
        Fixture f = fixture();
        UUID unprovisioned = account("SELLER");
        mvc.perform(get("/seller/orders").header("Authorization", token(unprovisioned, "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/seller/orders").param("limit", "101")
                .header("Authorization", token(unprovisioned, "SELLER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/seller/orders").param("cursor", "foreign-cursor")
                .header("Authorization", token(unprovisioned, "SELLER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA())
                .header("Authorization", token(unprovisioned, "SELLER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT payment_status FROM marketplace.seller_order WHERE id=?", String.class, f.portionA()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE resource_id=?", Integer.class, f.portionA()))
                .isZero();
    }

    @Test void auditFailureRollsBackPayment() throws Exception {
        Fixture f = fixture();
        String constraint = "reject_cod_" + f.portionA().toString().replace("-", "");
        jdbc.execute("ALTER TABLE marketplace.audit_entry ADD CONSTRAINT " + constraint
                + " CHECK (action <> 'SELLER_ORDER_PAID' OR resource_id <> '" + f.portionA() + "'::uuid)");
        try {
            mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA()).header("Authorization", token(f.sellerA(), "SELLER")))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
            assertThat(jdbc.queryForObject("SELECT payment_status FROM marketplace.seller_order WHERE id=?", String.class, f.portionA()))
                    .isEqualTo("PENDING_PAYMENT");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE resource_id=?", Integer.class, f.portionA()))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE marketplace.audit_entry DROP CONSTRAINT " + constraint);
        }
    }

    @Test void sellerPagesArePrivateAndCursorIsScoped() throws Exception {
        Fixture f = fixture();
        Fixture second = fixtureFor(f.member(), f.sellerA(), f.shopA(), f.sellerB(), f.shopB());
        Instant tied = Instant.parse("2026-11-13T08:00:00Z");
        jdbc.update("UPDATE marketplace.customer_order SET created_at=? WHERE id IN (?,?)",
                java.sql.Timestamp.from(tied), f.order(), second.order());
        String body = mvc.perform(get("/seller/orders").param("limit", "1")
                .header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true)).andReturn().getResponse().getContentAsString();
        JsonNode first = json.readTree(body);
        assertThat(first.path("items").get(0).path("sellerId").asText()).isEqualTo(f.shopA().toString());
        assertThat(first.path("items").get(0).has("memberId")).isFalse();
        assertThat(first.path("items").get(0).path("lines").size()).isEqualTo(1);
        assertThat(first.path("items").get(0).path("lines").get(0).path("productName").asText()).isEqualTo("COD title");
        assertThat(first.path("items").get(0).path("totalUsd").asText()).isEqualTo("12.50");
        String cursor = first.path("nextCursor").asText();
        String nextBody = mvc.perform(get("/seller/orders").param("limit", "1").param("cursor", cursor)
                .header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode next = json.readTree(nextBody);
        assertThat(next.path("items").get(0).path("sellerId").asText()).isEqualTo(f.shopA().toString());
        assertThat(List.of(first.path("items").get(0).path("id").asText(), next.path("items").get(0).path("id").asText()))
                .containsExactlyInAnyOrder(f.portionA().toString(), second.portionA().toString());
        assertThat(next.path("nextCursor").isNull()).isTrue();
        mvc.perform(get("/seller/orders").param("cursor", cursors.encodeTimed("seller-orders", f.shopA().toString(),
                        tied.minusSeconds(1), UUID.randomUUID()))
                .header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/seller/orders").param("cursor", cursor + "x")
                .header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/seller/orders").param("cursor", cursor)
                .header("Authorization", token(f.sellerB(), "SELLER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/seller/orders").param("limit", "101")
                .header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/seller/orders").header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/seller/orders").param("cursor", cursor).param("limit", "1")
                .header("Authorization", token(f.sellerA(), "SELLER")))
                .andExpect(status().isOk());
        mvc.perform(get("/seller/orders").param("cursor", cursor).param("limit", "1")
                .header("Authorization", token(f.sellerB(), "SELLER")))
                .andExpect(status().isBadRequest());
    }

    @Test void twoSellersMarkConcurrentlyWithoutLostPayment() throws Exception {
        Fixture f = fixture();
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = pool.submit(() -> markAfter(start, f.sellerA(), f.portionA()));
            var second = pool.submit(() -> markAfter(start, f.sellerB(), f.portionB()));
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.seller_order WHERE customer_order_id=? AND payment_status='PAID'",
                Integer.class, f.order())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='SELLER_ORDER_PAID' AND resource_id IN (?,?)",
                Integer.class, f.portionA(), f.portionB())).isEqualTo(2);
        mvc.perform(get("/orders/{id}", f.order()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test void cancelWinsRootLockBeforePayment() throws Exception {
        Fixture f = fixture();
        raceWithQueuedWinner(f, true);
        assertRaceResult(f, "CANCELLED", 0, 4, 1, 1, 0);
    }

    @Test void paymentWinsRootLockBeforeCancel() throws Exception {
        Fixture f = fixture();
        raceWithQueuedWinner(f, false);
        assertRaceResult(f, "PLACED", 1, 3, 0, 0, 1);
    }

    private void raceWithQueuedWinner(Fixture f, boolean cancelFirst) throws Exception {
        try (Connection gate = dataSource.getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            try (var lock = gate.prepareStatement("SELECT id FROM marketplace.customer_order WHERE id=? FOR UPDATE")) {
                lock.setObject(1, f.order());
                lock.executeQuery().close();
            }
            try {
                var first = pool.submit(() -> cancelFirst ? cancelStatus(f) : markStatus(f));
                awaitRootWaiters(1);
                var second = pool.submit(() -> cancelFirst ? markStatus(f) : cancelStatus(f));
                awaitRootWaiters(2);
                gate.commit();
                assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(409);
            } finally {
                gate.rollback();
            }
        }
    }

    private void awaitRootWaiters(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        while (System.nanoTime() < deadline) {
            waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' "
                    + "AND query LIKE 'SELECT id,% FROM marketplace.customer_order%FOR UPDATE' AND pid<>pg_backend_pid()",
                    Integer.class);
            if (waiting >= expected) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected " + expected + " queued root-lock waiters; observed " + waiting);
    }

    private int cancelStatus(Fixture f) throws Exception {
        return mvc.perform(post("/orders/{id}/cancellation", f.order())
                .header("Authorization", token(f.member(), "MEMBER"))).andReturn().getResponse().getStatus();
    }

    private int markStatus(Fixture f) throws Exception {
        return mvc.perform(post("/seller/orders/{id}/mark-paid", f.portionA())
                .header("Authorization", token(f.sellerA(), "SELLER"))).andReturn().getResponse().getStatus();
    }

    private void assertRaceResult(Fixture f, String orderStatus,
            int paidPortions, int stockOnHand, int stockVersion, int cancellationAudits, int paymentAudits) {
        JdbcTemplate committed = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(committed.queryForObject("SELECT status FROM marketplace.customer_order WHERE id=?", String.class, f.order()))
                .isEqualTo(orderStatus);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.seller_order WHERE customer_order_id=? AND payment_status='PAID'",
                Integer.class, f.order())).isEqualTo(paidPortions);
        assertThat(committed.queryForList("SELECT st.on_hand FROM marketplace.stock st JOIN marketplace.order_line l "
                + "ON l.variant_id=st.variant_id JOIN marketplace.seller_order so ON so.id=l.seller_order_id "
                + "WHERE so.customer_order_id=? ORDER BY st.variant_id", Integer.class, f.order()))
                .containsExactly(stockOnHand, stockOnHand);
        assertThat(committed.queryForList("SELECT st.version FROM marketplace.stock st JOIN marketplace.order_line l "
                + "ON l.variant_id=st.variant_id JOIN marketplace.seller_order so ON so.id=l.seller_order_id "
                + "WHERE so.customer_order_id=? ORDER BY st.variant_id", Long.class, f.order()))
                .containsExactly((long) stockVersion, (long) stockVersion);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='ORDER_CANCELLED' AND resource_id=?",
                Integer.class, f.order())).isEqualTo(cancellationAudits);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='SELLER_ORDER_PAID' AND resource_id=?",
                Integer.class, f.portionA())).isEqualTo(paymentAudits);
    }

    private int markAfter(CountDownLatch start, UUID seller, UUID portion) throws Exception {
        start.await();
        return mvc.perform(post("/seller/orders/{id}/mark-paid", portion)
                .header("Authorization", token(seller, "SELLER")))
                .andReturn().getResponse().getStatus();
    }

    private Fixture fixture() {
        UUID member = account("MEMBER"), sellerA = account("SELLER"), sellerB = account("SELLER");
        UUID shopA = shop(sellerA), shopB = shop(sellerB);
        return fixtureFor(member, sellerA, shopA, sellerB, shopB);
    }
    private Fixture fixtureFor(UUID member, UUID sellerA, UUID shopA, UUID sellerB, UUID shopB) {
        UUID category = UUID.randomUUID(), order = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?,?)", category, "COD fixture");
        jdbc.update("INSERT INTO marketplace.customer_order(id,member_id,idempotency_key,request_fingerprint,status,total_usd,currency) "
                + "VALUES (?,?,?,?,'PLACED',25.00,'USD')", order, member, UUID.randomUUID().toString(), "fixture");
        jdbc.update("INSERT INTO marketplace.order_address_snapshot(customer_order_id,recipient,address_line,locality,postal_code,country) "
                + "VALUES (?,'A','1 St','Hanoi','10000','VN')", order);
        UUID a = portion(order, shopA, category), b = portion(order, shopB, category);
        return new Fixture(member, sellerA, sellerB, shopA, shopB, order, a, b);
    }
    private UUID portion(UUID order, UUID shop, UUID category) {
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID(), portion = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,?,?,'COD title','Fixture',true)",
                product, shop, category);
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,?,'Fixture',12.50)",
                variant, product, "COD-" + variant);
        jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand) VALUES (?,3)", variant);
        jdbc.update("INSERT INTO marketplace.seller_order(id,customer_order_id,seller_id,payment_status,total_usd) "
                + "VALUES (?,?,?,'PENDING_PAYMENT',12.50)", portion, order, shop);
        jdbc.update("INSERT INTO marketplace.order_line(id,seller_order_id,variant_id,product_id,product_title_snapshot,sku_snapshot,quantity,unit_price_usd,discount_usd,line_total_usd) "
                + "VALUES (?,?,?,?,'COD title','COD-SKU',1,12.50,0,12.50)", UUID.randomUUID(), portion, variant, product);
        return portion;
    }
    private UUID shop(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')", id, account, "COD shop");
        return id;
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) "
                + "VALUES (?,?,?,?,'ACTIVE','Fixture')", id, id + "@example.test", "fixture", role);
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
    private record Fixture(UUID member, UUID sellerA, UUID sellerB, UUID shopA, UUID shopB,
                           UUID order, UUID portionA, UUID portionB) {}
}

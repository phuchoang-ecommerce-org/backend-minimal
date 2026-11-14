package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@SpringBootTest(classes = MarketplaceApplication.class)
class CheckoutPostgresRedTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("checkout_red").withPassword("checkout_red");
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
    @Autowired DataSource dataSource;

    @Test void checkoutCancellationAndSellerEditShareStockVersion() throws Exception {
        Fixture f = fixture("http-stock-version", 1, 1);
        UUID seller = jdbc.queryForObject("SELECT account_id FROM marketplace.seller_shop WHERE id=?",
                UUID.class, f.firstShop());
        UUID product = jdbc.queryForObject("SELECT product_id FROM marketplace.variant WHERE id=?",
                UUID.class, f.firstVariant());
        JsonNode placed = json(place(f.member(), f.address(), 1, "http-stock-version-key", null)
                .andExpect(status().isCreated()).andReturn());
        UUID order = UUID.fromString(placed.path("id").asText());
        assertThat(stock(f.firstVariant())).isEqualTo(4);
        assertThat(stockVersion(f.firstVariant())).isEqualTo(1);
        staleStockEdit(seller, f.firstVariant(), 0);
        mvc.perform(post("/orders/{id}/cancellation", order)
                .header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        staleStockEdit(seller, f.firstVariant(), 1);
        mvc.perform(get("/orders/{id}", order).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/seller/products/{id}", product).header("Authorization", token(seller, "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].stock.onHand").value(5))
                .andExpect(jsonPath("$.variants[0].stock.version").value(2));
        JdbcTemplate committed = committed();
        assertThat(committed.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?",
                Integer.class, f.firstVariant())).isEqualTo(5);
        assertThat(committed.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?",
                Long.class, f.firstVariant())).isEqualTo(2);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE resource_id=?",
                Integer.class, order)).isEqualTo(2);
    }

    private void staleStockEdit(UUID seller, UUID variant, long version) throws Exception {
        mvc.perform(patch("/seller/stock/{id}", variant).header("Authorization", token(seller, "SELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"onHand\":10,\"expectedVersion\":" + version + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test void twoSellerCheckoutSnapshotsAndConsumesOneCartAtomically() throws Exception {
        Fixture f = fixture("two-seller", 2, 1);
        JsonNode body = json(place(f.member(), f.address(), 1, "two-seller-key", null)
                .andExpect(status().isCreated()).andReturn());
        assertThat(body.path("memberId").asText()).isEqualTo(f.member().toString());
        assertThat(body.path("status").asText()).isEqualTo("PLACED");
        assertThat(body.path("paymentStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(body.path("paymentMethod").asText()).isEqualTo("COD");
        assertThat(body.path("totalUsd").asText()).isEqualTo("32.25");
        assertThat(body.path("address").path("recipient").asText()).isEqualTo("two-seller");
        assertThat(body.path("discountUsd").asText()).isEqualTo("0.00");
        assertThat(body.path("sellerOrders").size()).isEqualTo(2);
        assertThat(body.path("sellerOrders").findValuesAsText("sellerId"))
                .containsExactlyInAnyOrder(f.firstShop().toString(), f.secondShop().toString());
        assertThat(body.path("sellerOrders").findValuesAsText("paymentStatus"))
                .containsOnly("PENDING_PAYMENT");
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isEqualTo(1);
        assertThat(count("marketplace.seller_order", "customer_order_id", UUID.fromString(body.path("id").asText()))).isEqualTo(2);
        assertThat(count("marketplace.order_line", "seller_order_id", UUID.fromString(body.path("sellerOrders").get(0).path("id").asText()))
                + count("marketplace.order_line", "seller_order_id", UUID.fromString(body.path("sellerOrders").get(1).path("id").asText()))).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT unit_price_usd::text FROM marketplace.order_line l "
                + "JOIN marketplace.seller_order s ON s.id=l.seller_order_id WHERE s.customer_order_id=?",
                String.class, UUID.fromString(body.path("id").asText())))
                .containsExactlyInAnyOrder("12.50", "7.25");
        assertThat(jdbc.queryForObject("SELECT recipient FROM marketplace.order_address_snapshot WHERE customer_order_id=?",
                String.class, UUID.fromString(body.path("id").asText()))).isEqualTo("two-seller");
        assertThat(stock(f.firstVariant())).isEqualTo(3);
        assertThat(stock(f.secondVariant())).isEqualTo(4);
        assertThat(stockVersion(f.firstVariant())).isEqualTo(1L);
        assertThat(stockVersion(f.secondVariant())).isEqualTo(1L);
        assertThat(count("marketplace.cart_line", "cart_id", f.cart())).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM marketplace.cart WHERE id=?", Long.class, f.cart())).isEqualTo(2L);
        assertThat(count("marketplace.audit_entry", "resource_id", UUID.fromString(body.path("id").asText()))).isEqualTo(1);
    }

    @Test void foreignAddressAndMissingAddressBothReturn404WithoutWrites() throws Exception {
        Fixture f = fixture("foreign-address", 1, 1);
        UUID other = member("foreign-address-other");
        UUID foreignAddress = address("foreign-address-other", other);
        place(f.member(), foreignAddress, 1, "foreign-address-key", null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        place(f.member(), UUID.randomUUID(), 1, "missing-address-key", null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isZero();
        assertThat(stock(f.firstVariant())).isEqualTo(5);
        assertThat(count("marketplace.cart_line", "cart_id", f.cart())).isEqualTo(2);
    }

    @Test void staleCartVersionAndUnknownVoucherRejectWithoutConsumingKey() throws Exception {
        Fixture f = fixture("stale-voucher", 1, 1);
        place(f.member(), f.address(), 0, "stale-key", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_CHANGED"));
        place(f.member(), f.address(), 1, "voucher-key", "NO-SUCH-VOUCHER")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isZero();
        assertThat(stock(f.firstVariant())).isEqualTo(5);
        assertThat(stock(f.secondVariant())).isEqualTo(5);
        assertThat(count("marketplace.cart_line", "cart_id", f.cart())).isEqualTo(2);
    }

    @Test void unpublishedOrInactiveOfferRejectsEntireMixedSellerCheckout() throws Exception {
        Fixture f = fixture("ineligible-offer", 1, 1);
        jdbc.update("UPDATE marketplace.product SET published=false WHERE id=(SELECT product_id FROM marketplace.variant WHERE id=?)",
                f.secondVariant());
        place(f.member(), f.address(), 1, "unpublished-key", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_UNAVAILABLE"));
        jdbc.update("UPDATE marketplace.product SET published=true WHERE id=(SELECT product_id FROM marketplace.variant WHERE id=?)",
                f.secondVariant());
        jdbc.update("UPDATE marketplace.seller_shop SET status='SUSPENDED' WHERE id=?", f.secondShop());
        place(f.member(), f.address(), 1, "inactive-seller-key", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_UNAVAILABLE"));
        assertThat(stock(f.firstVariant())).isEqualTo(5);
        assertThat(stock(f.secondVariant())).isEqualTo(5);
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isZero();
    }

    @Test void checkoutRequiresMemberBearerAndDoesNotAcceptSellerRole() throws Exception {
        Fixture f = fixture("role-boundary", 1, 1);
        String body = "{\"addressId\":\"" + f.address() + "\",\"cartVersion\":1,\"paymentMethod\":\"COD\"}";
        mvc.perform(post("/orders").with(csrf()).header("Idempotency-Key", "guest-key")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post("/orders").with(csrf()).header("Authorization", "Bearer malformed")
                .header("Idempotency-Key", "bad-token-key").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        UUID sellerAccount = jdbc.queryForObject("SELECT account_id FROM marketplace.seller_shop WHERE id=?", UUID.class, f.firstShop());
        mvc.perform(post("/orders").with(csrf()).header("Authorization", token(sellerAccount, "SELLER"))
                .header("Idempotency-Key", "seller-key").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_DENIED"));
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isZero();
    }

    @Test void sameKeyReplaysOriginalCreationSnapshotAndChangedBodyConflicts() throws Exception {
        Fixture f = fixture("replay", 1, 1);
        JsonNode first = json(place(f.member(), f.address(), 1, "replay-key", null)
                .andExpect(status().isCreated()).andReturn());
        jdbc.update("UPDATE marketplace.address SET recipient='Changed after order' WHERE id=?", f.address());
        JsonNode replay = json(place(f.member(), f.address(), 1, "replay-key", null)
                .andExpect(status().isCreated()).andReturn());
        assertThat(replay).isEqualTo(first);
        place(f.member(), f.address(), 1, "replay-key", "DIFFERENT")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isEqualTo(1);
        assertThat(stock(f.firstVariant())).isEqualTo(4);
        assertThat(stock(f.secondVariant())).isEqualTo(4);
    }

    @Test void twoMembersRacingForLastUnitCommitOnlyOneOrder() throws Exception {
        Fixture f = fixture("last-unit", 1, 0);
        jdbc.update("UPDATE marketplace.stock SET on_hand=1 WHERE variant_id=?", f.firstVariant());
        UUID second = member("last-unit-other");
        UUID secondAddress = address("last-unit-other", second);
        UUID secondCart = cart("last-unit-other", second, f.firstVariant(), null);
        jdbc.update("DELETE FROM marketplace.cart_line WHERE cart_id=? AND variant_id=?", f.cart(), f.secondVariant());
        try (Connection gate = dataSource.getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            try (var lock = gate.prepareStatement("SELECT id FROM marketplace.variant WHERE id=? FOR UPDATE")) {
                lock.setObject(1, f.firstVariant());
                lock.executeQuery().close();
            }
            try {
                var first = pool.submit(() -> place(f.member(), f.address(), 1, "last-unit-first", null).andReturn());
                var other = pool.submit(() -> place(second, secondAddress, 1, "last-unit-other", null).andReturn());
                awaitLockWaiters("SELECT p.id,p.seller_id,v.id%", 2);
                gate.commit();
                MvcResult a = first.get(20, TimeUnit.SECONDS);
                MvcResult b = other.get(20, TimeUnit.SECONDS);
                assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
                        .containsExactlyInAnyOrder(201, 409);
                assertThat(json(a.getResponse().getStatus() == 409 ? a : b).path("code").asText())
                        .isEqualTo("STOCK_UNAVAILABLE");
            } finally {
                gate.rollback();
            }
        }
        JdbcTemplate committed = committed();
        assertThat(committed.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?",
                Integer.class, f.firstVariant())).isZero();
        assertThat(committed.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?",
                Long.class, f.firstVariant())).isEqualTo(1);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.customer_order WHERE member_id IN (?,?)", Integer.class,
                f.member(), second)).isEqualTo(1);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.cart_line WHERE cart_id IN (?,?)",
                Integer.class, secondCart, f.cart())).isEqualTo(1);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='ORDER_PLACED' "
                + "AND actor_id IN (?,?)", Integer.class, f.member(), second)).isEqualTo(1);
    }

    @Test void concurrentIdenticalKeyReturnsOneOriginalOrderAndOneStockDecrement() throws Exception {
        Fixture f = fixture("same-key-race", 1, 0);
        jdbc.update("DELETE FROM marketplace.cart_line WHERE cart_id=? AND variant_id=?", f.cart(), f.secondVariant());
        try (Connection gate = dataSource.getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            try (var lock = gate.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                lock.setString(1, f.member() + ":same-key-race");
                lock.executeQuery().close();
            }
            try {
                var first = pool.submit(() -> place(f.member(), f.address(), 1, "same-key-race", null).andReturn());
                var second = pool.submit(() -> place(f.member(), f.address(), 1, "same-key-race", null).andReturn());
                awaitLockWaiters("SELECT pg_advisory_xact_lock%", 2);
                gate.commit();
                MvcResult a = first.get(20, TimeUnit.SECONDS);
                MvcResult b = second.get(20, TimeUnit.SECONDS);
                assertThat(a.getResponse().getStatus()).isEqualTo(201);
                assertThat(b.getResponse().getStatus()).isEqualTo(201);
                assertThat(json(a)).isEqualTo(json(b));
            } finally {
                gate.rollback();
            }
        }
        JdbcTemplate committed = committed();
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.customer_order WHERE member_id=? AND idempotency_key=?",
                Integer.class, f.member(), "same-key-race")).isEqualTo(1);
        assertThat(committed.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?",
                Integer.class, f.firstVariant())).isEqualTo(4);
        assertThat(committed.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?",
                Long.class, f.firstVariant())).isEqualTo(1);
        assertThat(committed.queryForObject("SELECT count(*) FROM marketplace.audit_entry WHERE action='ORDER_PLACED' AND actor_id=?",
                Integer.class, f.member())).isEqualTo(1);
    }

    private void awaitLockWaiters(String queryPattern, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        while (System.nanoTime() < deadline) {
            waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' "
                    + "AND query LIKE ? AND pid<>pg_backend_pid()", Integer.class, queryPattern);
            if (waiting >= expected) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected " + expected + " lock waiters for " + queryPattern + "; observed " + waiting);
    }

    private JdbcTemplate committed() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test void lateAuditFailureRollsBackStockOrderCartAndKey() throws Exception {
        Fixture f = fixture("late-failure", 1, 1);
        jdbc.execute("CREATE FUNCTION marketplace.checkout_red_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'checkout red late failure'; END; $$");
        jdbc.execute("CREATE TRIGGER checkout_red_audit_failure BEFORE INSERT ON marketplace.audit_entry FOR EACH ROW EXECUTE FUNCTION marketplace.checkout_red_audit_failure()");
        try {
            place(f.member(), f.address(), 1, "late-failure-key", null).andExpect(status().isInternalServerError());
            assertThat(count("marketplace.customer_order", "member_id", f.member())).isZero();
            assertThat(stock(f.firstVariant())).isEqualTo(5);
            assertThat(stock(f.secondVariant())).isEqualTo(5);
            assertThat(count("marketplace.cart_line", "cart_id", f.cart())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT version FROM marketplace.cart WHERE id=?", Long.class, f.cart())).isEqualTo(1L);
        } finally {
            jdbc.execute("DROP TRIGGER checkout_red_audit_failure ON marketplace.audit_entry");
            jdbc.execute("DROP FUNCTION marketplace.checkout_red_audit_failure()");
        }
        place(f.member(), f.address(), 1, "late-failure-key", null).andExpect(status().isCreated());
        assertThat(count("marketplace.customer_order", "member_id", f.member())).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions place(UUID member, UUID address, long version,
            String key, String voucher) throws Exception {
        String body = "{\"addressId\":\"" + address + "\",\"cartVersion\":" + version
                + ",\"paymentMethod\":\"COD\"" + (voucher == null ? "" : ",\"voucherCode\":\"" + voucher + "\"") + "}";
        return mvc.perform(post("/orders").with(csrf()).header("Authorization", token(member, "MEMBER"))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private Fixture fixture(String name, int firstQuantity, int secondQuantity) {
        UUID member = member(name);
        UUID address = address(name, member);
        UUID firstSeller = seller(name + "-first");
        UUID secondSeller = seller(name + "-second");
        UUID firstShop = shop(name + "-first", firstSeller);
        UUID secondShop = shop(name + "-second", secondSeller);
        UUID category = id(name + "-category");
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?,?)", category, name);
        UUID firstVariant = offer(name + "-first", firstShop, category, "12.50");
        UUID secondVariant = offer(name + "-second", secondShop, category, "7.25");
        UUID cart = cart(name, member, firstVariant, secondVariant);
        jdbc.update("UPDATE marketplace.cart_line SET quantity=? WHERE cart_id=? AND variant_id=?", firstQuantity, cart, firstVariant);
        if (secondQuantity > 0) {
            jdbc.update("UPDATE marketplace.cart_line SET quantity=? WHERE cart_id=? AND variant_id=?", secondQuantity, cart, secondVariant);
        }
        return new Fixture(member, address, cart, firstShop, secondShop, firstVariant, secondVariant);
    }

    private UUID member(String name) {
        UUID id = id(name + "-member");
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) VALUES (?,?,?,'MEMBER','ACTIVE',?)",
                id, name + "@example.test", "test-only-hash", name);
        return id;
    }

    private UUID seller(String name) {
        UUID id = id(name + "-account");
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) VALUES (?,?,?,'SELLER','ACTIVE',?)",
                id, name + "@example.test", "test-only-hash", name);
        return id;
    }

    private UUID shop(String name, UUID account) {
        UUID id = id(name + "-shop");
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')", id, account, name);
        return id;
    }

    private UUID address(String name, UUID member) {
        UUID id = id(name + "-address");
        jdbc.update("INSERT INTO marketplace.address(id,member_id,recipient,address_line,locality,postal_code,country) VALUES (?,?,?,?,?,?,?)",
                id, member, name, "1 Main St", "Hanoi", "10000", "VN");
        return id;
    }

    private UUID offer(String name, UUID shop, UUID category, String price) {
        UUID product = id(name + "-product");
        UUID variant = id(name + "-variant");
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,?,?,?,?,true)",
                product, shop, category, name, "Checkout fixture");
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,?,?,?::numeric)",
                variant, product, name + "-sku", name, price);
        jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand) VALUES (?,5)", variant);
        return variant;
    }

    private UUID cart(String name, UUID member, UUID first, UUID second) {
        UUID id = id(name + "-cart");
        jdbc.update("INSERT INTO marketplace.cart(id,member_id,version) VALUES (?,?,1)", id, member);
        jdbc.update("INSERT INTO marketplace.cart_line(cart_id,variant_id,quantity) VALUES (?,?,1)", id, first);
        if (second != null) {
            jdbc.update("INSERT INTO marketplace.cart_line(cart_id,variant_id,quantity) VALUES (?,?,1)", id, second);
        }
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

    private int stock(UUID variant) {
        return jdbc.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?", Integer.class, variant);
    }

    private long stockVersion(UUID variant) {
        return jdbc.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?", Long.class, variant);
    }

    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + "=?", Integer.class, id);
    }

    private UUID id(String name) {
        return UUID.nameUUIDFromBytes(("checkout-red-" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Fixture(UUID member, UUID address, UUID cart, UUID firstShop, UUID secondShop,
                           UUID firstVariant, UUID secondVariant) {
    }
}

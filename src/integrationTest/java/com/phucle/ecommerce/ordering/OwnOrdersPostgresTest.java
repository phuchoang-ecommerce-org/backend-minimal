package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
@Import(OwnOrdersPostgresTest.QueryProbeConfig.class)
class OwnOrdersPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("own_orders").withPassword("own_orders");
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
    @Autowired InstrumentedJdbcTemplate queryProbe;

    @Test void pageHydratesMaximumSizeInBoundedQueriesAndMatchesOrderSchema() throws Exception {
        Fixture f = fixture();
        for (int index = 0; index < 100; index++) order(f, f.member(), Instant.parse("2026-11-13T08:00:00Z"));
        queryProbe.resetProbe();
        JsonNode one = body(mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER"))
                .param("limit", "1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(one.path("items").size()).isEqualTo(1);
        assertThat(queryProbe.orderingSelectCount()).isLessThanOrEqualTo(5);
        queryProbe.resetProbe();
        JsonNode maximum = body(mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER"))
                .param("limit", "100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(maximum.path("items").size()).isEqualTo(100);
        assertThat(queryProbe.orderingSelectCount()).isLessThanOrEqualTo(5);
        JsonNode item = maximum.path("items").get(0);
        assertThat(item.path("memberId").asText()).isEqualTo(f.member().toString());
        assertThat(item.path("status").asText()).isEqualTo("PLACED");
        assertThat(item.path("paymentStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(item.path("paymentMethod").asText()).isEqualTo("COD");
        assertThat(item.path("totalUsd").asText()).isEqualTo("12.50");
        assertThat(item.path("discountUsd").asText()).isEqualTo("0.00");
        assertThat(item.path("voucherCode").isNull()).isTrue();
        assertThat(item.path("sellerOrders").get(0).path("paidAt").isNull()).isTrue();
        assertThat(item.path("sellerOrders").get(0).path("lines").get(0).path("lineTotalUsd").isMissingNode()).isTrue();
        assertThat(item.path("sellerOrders").get(0).path("lines").get(0).path("totalUsd").asText()).isEqualTo("12.50");
        Instant.parse(item.path("createdAt").asText());
        assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "memberId", "status", "paymentStatus", "totalUsd",
                "sellerOrders", "createdAt", "address", "discountUsd", "paymentMethod", "voucherCode");
        assertThat(fieldNames(item.path("address"))).containsExactlyInAnyOrder("recipient", "addressLine", "locality", "postalCode", "country");
        assertThat(fieldNames(item.path("sellerOrders").get(0))).containsExactlyInAnyOrder("id", "sellerId", "paymentStatus",
                "totalUsd", "createdAt", "orderId", "status", "lines", "paidAt");
        assertThat(fieldNames(item.path("sellerOrders").get(0).path("lines").get(0))).containsExactlyInAnyOrder(
                "productId", "variantId", "productName", "sku", "quantity", "unitPriceUsd", "discountUsd", "totalUsd");
    }

    @Test void detailUsesOneCommittedSnapshotWhenPaymentCommitsBetweenReads() throws Exception {
        Fixture f = fixture();
        UUID order = order(f, f.member(), Instant.parse("2026-11-13T08:00:00Z"));
        UUID portion = jdbc.queryForObject("SELECT id FROM marketplace.seller_order WHERE customer_order_id=?", UUID.class, order);
        JdbcTemplate independent = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        AtomicBoolean committed = new AtomicBoolean();
        queryProbe.afterRootRead(() -> {
            independent.update("UPDATE marketplace.seller_order SET payment_status='PAID',paid_at=now() WHERE id=?", portion);
            committed.set(true);
        });
        JsonNode during = body(mvc.perform(get("/orders/{orderId}", order)
                .header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(committed.get()).isTrue();
        assertThat(during.path("paymentStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(during.path("sellerOrders").get(0).path("paymentStatus").asText()).isEqualTo("PENDING_PAYMENT");
        mvc.perform(get("/orders/{orderId}", order).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test void detailUsesSnapshotsAndHidesForeignOrder() throws Exception {
        Fixture f = fixture();
        UUID order = order(f, f.member(), Instant.parse("2026-11-13T08:00:00Z"));
        UUID currentAddress = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.address(id,member_id,recipient,address_line,locality,postal_code,country) "
                + "VALUES (?,?,'Original recipient','1 Original St','Hanoi','10000','VN')", currentAddress, f.member());
        jdbc.update("UPDATE marketplace.address SET recipient='Changed recipient' WHERE id=?", currentAddress);
        jdbc.update("UPDATE marketplace.product SET title='Changed title' WHERE id=?", f.product());
        jdbc.update("UPDATE marketplace.variant SET price_usd=99.99,sku='CHANGED-SKU' WHERE id=?", f.variant());
        mvc.perform(get("/orders/{orderId}", order).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address.recipient").value("Original recipient"))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].productName").value("Original title"))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].sku").value("ORIGINAL-SKU"))
                .andExpect(jsonPath("$.sellerOrders[0].lines[0].unitPriceUsd").value("12.50"));
        mvc.perform(get("/orders/{orderId}", order).header("Authorization", token(f.other(), "MEMBER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/orders/{orderId}", UUID.randomUUID()).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test void paymentProjectionRequiresEveryPortionPaid() throws Exception {
        Fixture f = fixture();
        UUID order = order(f, f.member(), Instant.parse("2026-11-13T08:00:00Z"));
        UUID secondShop = shop();
        UUID secondPortion = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.seller_order(id,customer_order_id,seller_id,payment_status,total_usd) VALUES (?,?,?,'PENDING_PAYMENT',0)",
                secondPortion, order, secondShop);
        UUID firstPortion = jdbc.queryForObject("SELECT id FROM marketplace.seller_order WHERE customer_order_id=? AND id<>?", UUID.class,
                order, secondPortion);
        jdbc.update("UPDATE marketplace.seller_order SET payment_status='PAID',paid_at=now() WHERE id=?", firstPortion);
        mvc.perform(get("/orders/{orderId}", order).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PENDING_PAYMENT"));
        jdbc.update("UPDATE marketplace.seller_order SET payment_status='PAID',paid_at=now() WHERE id=?", secondPortion);
        mvc.perform(get("/orders/{orderId}", order).header("Authorization", token(f.member(), "MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test void pageUsesMemberBoundTimestampCursor() throws Exception {
        Fixture f = fixture();
        Instant sameTime = Instant.parse("2026-11-13T08:00:00.123456Z");
        UUID a = order(f, f.member(), sameTime);
        UUID b = order(f, f.member(), sameTime);
        order(f, f.other(), sameTime);
        JsonNode first = body(mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER"))
                .param("limit", "1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.path("items").size()).isEqualTo(1);
        assertThat(first.path("items").get(0).path("id").asText()).isEqualTo(a.toString().compareTo(b.toString()) > 0 ? a.toString() : b.toString());
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        String cursor = first.path("nextCursor").asText();
        JsonNode second = body(mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER"))
                .param("limit", "1").param("cursor", cursor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(second.path("items").size()).isEqualTo(1);
        assertThat(second.path("items").get(0).path("id").asText()).isEqualTo(a.toString().compareTo(b.toString()) > 0 ? b.toString() : a.toString());
        assertThat(second.path("hasNext").asBoolean()).isFalse();
        assertThat(second.path("nextCursor").isNull()).isTrue();
        mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER"))
                .param("cursor", "x" + cursor.substring(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/orders").header("Authorization", token(f.other(), "MEMBER"))
                .param("cursor", cursor)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER")).param("limit", "101"))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE marketplace.customer_order SET created_at=? WHERE member_id=? AND id<>?",
                java.sql.Timestamp.from(sameTime.plusSeconds(1)), f.member(), UUID.fromString(first.path("items").get(0).path("id").asText()));
        JsonNode empty = body(mvc.perform(get("/orders").header("Authorization", token(f.member(), "MEMBER"))
                .param("cursor", cursor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("hasNext").asBoolean()).isFalse();
        assertThat(empty.path("nextCursor").isNull()).isTrue();
    }

    @Test void queryRequiresMember() throws Exception {
        Fixture f = fixture();
        UUID order = order(f, f.member(), Instant.now());
        mvc.perform(get("/orders/{orderId}", order)).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/{orderId}", order).header("Authorization", token(f.sellerAccount(), "SELLER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/orders").header("Authorization", token(f.sellerAccount(), "SELLER")))
                .andExpect(status().isForbidden());
    }

    private Fixture fixture() {
        UUID member = account("MEMBER");
        UUID other = account("MEMBER");
        UUID seller = account("SELLER");
        UUID shop = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')", shop, seller, "Fixture shop");
        UUID category = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?,?)", category, "Fixture category");
        UUID product = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,?,?,?,?,true)",
                product, shop, category, "Catalog title", "Fixture");
        UUID variant = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,?,?,12.50)",
                variant, product, "CATALOG-" + variant, "Fixture");
        return new Fixture(member, other, seller, shop, product, variant);
    }

    private UUID order(Fixture f, UUID member, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.customer_order(id,member_id,idempotency_key,request_fingerprint,status,total_usd,currency,created_at) "
                        + "VALUES (?,?,?,?,'PLACED',12.50,'USD',?)", id, member, UUID.randomUUID().toString(), "fixture",
                java.sql.Timestamp.from(createdAt));
        jdbc.update("INSERT INTO marketplace.order_address_snapshot(customer_order_id,recipient,address_line,locality,postal_code,country) "
                + "VALUES (?,'Original recipient','1 Original St','Hanoi','10000','VN')", id);
        UUID portion = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.seller_order(id,customer_order_id,seller_id,payment_status,total_usd) "
                + "VALUES (?,?,?,'PENDING_PAYMENT',12.50)", portion, id, f.shop());
        jdbc.update("INSERT INTO marketplace.order_line(id,seller_order_id,variant_id,product_id,product_title_snapshot,sku_snapshot,quantity,"
                + "unit_price_usd,discount_usd,line_total_usd) VALUES (?,?,?,?,'Original title','ORIGINAL-SKU',1,12.50,0,12.50)",
                UUID.randomUUID(), portion, f.variant(), f.product());
        return id;
    }

    private UUID shop() {
        UUID account = account("SELLER");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')", id, account, "Other shop");
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
    private JsonNode body(String response) throws Exception { return json.readTree(response); }
    private List<String> fieldNames(JsonNode value) {
        List<String> names = new java.util.ArrayList<>();
        value.fieldNames().forEachRemaining(names::add);
        return names;
    }
    private record Fixture(UUID member, UUID other, UUID sellerAccount, UUID shop, UUID product, UUID variant) {}

    @TestConfiguration
    static class QueryProbeConfig {
        @Bean @Primary JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new InstrumentedJdbcTemplate(dataSource);
        }
    }

    static class InstrumentedJdbcTemplate extends JdbcTemplate {
        private final AtomicInteger orderingSelects = new AtomicInteger();
        private volatile Runnable afterRootRead;
        InstrumentedJdbcTemplate(DataSource source) { super(source); }
        void resetProbe() { orderingSelects.set(0); afterRootRead = null; }
        int orderingSelectCount() { return orderingSelects.get(); }
        void afterRootRead(Runnable hook) { afterRootRead = hook; }
        @Override public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> result = super.query(sql, rowMapper, args);
            if (sql.startsWith("SELECT") && (sql.contains("marketplace.customer_order")
                    || sql.contains("marketplace.order_address_snapshot")
                    || sql.contains("marketplace.seller_order") || sql.contains("marketplace.order_line")))
                orderingSelects.incrementAndGet();
            if (sql.contains("marketplace.customer_order") && afterRootRead != null) {
                Runnable hook = afterRootRead;
                afterRootRead = null;
                hook.run();
            }
            return result;
        }
    }
}

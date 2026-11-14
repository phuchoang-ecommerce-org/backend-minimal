package com.phucle.ecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import com.phucle.ecommerce.inventory.api.CheckoutStock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class SellerStockPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("seller_stock").withPassword("seller_stock");
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
    @Autowired CheckoutStock checkoutStock;

    @Test void ownUnpublishedProductIsVisibleWhileSuspended() throws Exception {
        Fixture f = fixture(false);
        jdbc.update("UPDATE marketplace.seller_shop SET status='SUSPENDED' WHERE id=?", f.shop());
        mvc.perform(get("/seller/products/{id}", f.product()).header("Authorization", token(f.seller(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(f.product().toString()))
                .andExpect(jsonPath("$.sellerId").value(f.shop().toString()))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.variants[0].id").value(f.variant().toString()))
                .andExpect(jsonPath("$.variants[0].stock.onHand").value(3))
                .andExpect(jsonPath("$.variants[0].stock.version").value(0))
                .andExpect(jsonPath("$.ratingAverage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test void foreignResourcesAndRolesAreDenied() throws Exception {
        Fixture owner = fixture(true), foreign = fixture(true);
        mvc.perform(get("/seller/products/{id}", foreign.product()).header("Authorization", token(owner.seller(), "SELLER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/seller/products/{id}", UUID.randomUUID()).header("Authorization", token(owner.seller(), "SELLER")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/seller/stock/{id}", foreign.variant()).header("Authorization", token(owner.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":8,\"expectedVersion\":0}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(patch("/seller/stock/{id}", UUID.randomUUID()).header("Authorization", token(owner.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":8,\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/seller/products/{id}", owner.product()).header("Authorization", token(account("MEMBER"), "MEMBER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_DENIED"));
        mvc.perform(patch("/seller/stock/{id}", owner.variant()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"onHand\":8,\"expectedVersion\":0}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertStock(foreign.variant(), 3, 0);
    }

    @Test void absoluteEditRequiresCurrentVersion() throws Exception {
        Fixture f = fixture(true);
        mvc.perform(patch("/seller/stock/{id}", f.variant()).header("Authorization", token(f.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":8,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variantId").value(f.variant().toString()))
                .andExpect(jsonPath("$.onHand").value(8)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/seller/stock/{id}", f.variant()).header("Authorization", token(f.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":9,\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        for (String bad : List.of("{\"onHand\":-1,\"expectedVersion\":1}", "{\"onHand\":2}",
                "{\"onHand\":2,\"expectedVersion\":-1}", "{\"onHand\":2,\"expectedVersion\":1,\"sellerId\":\"x\"}",
                "{\"onHand\":2.5,\"expectedVersion\":1}", "{\"onHand\":2,\"expectedVersion\":1.5}")) {
            mvc.perform(patch("/seller/stock/{id}", f.variant()).header("Authorization", token(f.seller(), "SELLER"))
                    .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertStock(f.variant(), 8, 1);
    }

    @Test void competingAbsoluteEditsCannotBothUseOneVersion() throws Exception {
        Fixture f = fixture(true);
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = pool.submit(() -> replaceAfter(start, f, 8));
            var second = pool.submit(() -> replaceAfter(start, f, 9));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?", Integer.class, f.variant()))
                .isIn(8, 9);
        assertThat(jdbc.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?", Long.class, f.variant()))
                .isEqualTo(1);
    }

    private int replaceAfter(CountDownLatch start, Fixture f, int quantity) throws Exception {
        start.await();
        return mvc.perform(patch("/seller/stock/{id}", f.variant()).header("Authorization", token(f.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":" + quantity + ",\"expectedVersion\":0}"))
                .andReturn().getResponse().getStatus();
    }

    @Test void suspendedShopCannotWriteStock() throws Exception {
        Fixture f = fixture(true);
        jdbc.update("UPDATE marketplace.seller_shop SET status='SUSPENDED' WHERE id=?", f.shop());
        mvc.perform(patch("/seller/stock/{id}", f.variant()).header("Authorization", token(f.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":8,\"expectedVersion\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELLER_INACTIVE"));
        assertStock(f.variant(), 3, 0);
    }

    @Test void checkoutAndCancelInvalidateOldStockEdit() throws Exception {
        Fixture f = fixture(true);
        assertThat(checkoutStock.decrease(Map.of(f.variant(), 1))).isTrue();
        stale(f, 0);
        assertStock(f.variant(), 2, 1);
        checkoutStock.restore(Map.of(f.variant(), 1));
        stale(f, 1);
        assertStock(f.variant(), 3, 2);
        mvc.perform(get("/seller/products/{id}", f.product()).header("Authorization", token(f.seller(), "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].stock.version").value(2));
    }

    private void stale(Fixture f, long version) throws Exception {
        mvc.perform(patch("/seller/stock/{id}", f.variant()).header("Authorization", token(f.seller(), "SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"onHand\":10,\"expectedVersion\":" + version + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }
    private void assertStock(UUID variant, int quantity, long version) {
        assertThat(jdbc.queryForObject("SELECT on_hand FROM marketplace.stock WHERE variant_id=?", Integer.class, variant)).isEqualTo(quantity);
        assertThat(jdbc.queryForObject("SELECT version FROM marketplace.stock WHERE variant_id=?", Long.class, variant)).isEqualTo(version);
    }
    private Fixture fixture(boolean published) {
        UUID seller = account("SELLER"), shop = UUID.randomUUID(), category = UUID.randomUUID();
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')", shop, seller, "Stock shop");
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?,?)", category, "Stock category");
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,?,?,'Stock product','Fixture',?)",
                product, shop, category, published);
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,?,'Default',12.50)",
                variant, product, "STOCK-" + variant);
        jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand) VALUES (?,3)", variant);
        return new Fixture(seller, shop, product, variant);
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
    private record Fixture(UUID seller, UUID shop, UUID product, UUID variant) {}
}

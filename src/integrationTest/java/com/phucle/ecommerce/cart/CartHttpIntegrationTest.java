package com.phucle.ecommerce.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import com.phucle.ecommerce.cart.api.GuestCartMergeCommand;
import com.phucle.ecommerce.cart.api.MergeGuestCartOnLogin;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@SpringBootTest(classes = MarketplaceApplication.class)
class CartHttpIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("cart_test").withPassword("cart_test");
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MergeGuestCartOnLogin mergePort;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_RUNTIME_USER", POSTGRES::getUsername);
        registry.add("DB_RUNTIME_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_MIGRATION_USER", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_PRIVATE_KEY", JwtTestKeys::privateKey);
        registry.add("JWT_PUBLIC_KEY", JwtTestKeys::publicKey);
        registry.add("CURSOR_MAC_SECRET", () -> "test-only-cursor-mac-secret-at-least-32-bytes");
        registry.add("CART_ALLOWED_ORIGINS", () -> "https://shop.example.test");
    }
    @Test
    void anonymousGetCreatesVersionedGuestCart() throws Exception {
        var result = mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.totalUsd").value("0.00")).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=2592000");
    }
    @Test
    void guestEditsRequireOriginAndVersionAndNeverReserveStock() throws Exception {
        UUID variant = fixture();
        mockMvc.perform(put("/cart/lines/" + variant).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        String cookie = mockMvc.perform(get("/cart")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        var request = put("/cart/lines/" + variant).cookie(new Cookie("guest_cart", cookie))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2,\"expectedVersion\":0}");
        mockMvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORIGIN_DENIED"));
        mockMvc.perform(put("/cart/lines/" + variant).cookie(new Cookie("guest_cart", cookie))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.totalUsd").value("25.00"));
        mockMvc.perform(put("/cart/lines/" + variant).cookie(new Cookie("guest_cart", cookie))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT on_hand FROM marketplace.stock WHERE variant_id=?", Integer.class, variant)).isEqualTo(7);
    }
    @Test
    void memberLoginConsumesGuestCartOnce() throws Exception {
        UUID variant = fixture();
        String cookie = mockMvc.perform(get("/cart")).andReturn().getResponse().getHeader("Set-Cookie")
                .split(";", 2)[0].split("=", 2)[1];
        mockMvc.perform(put("/cart/lines/" + variant).cookie(new Cookie("guest_cart", cookie))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        String login = "cart-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\""+login+"\",\"password\":\"long-test-password\",\"displayName\":\"Cart Member\"}"))
                .andExpect(status().isCreated());
        var first = mockMvc.perform(post("/auth/login").cookie(new Cookie("guest_cart", cookie))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\""+login+"\",\"password\":\"long-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cartMerge.merged").value(true)).andReturn();
        String token = json.readTree(first.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/cart").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].quantity").value(2));
        mockMvc.perform(post("/auth/login").cookie(new Cookie("guest_cart", cookie))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\""+login+"\",\"password\":\"long-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cartMerge.merged").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mockMvc.perform(get("/cart").header("Authorization", "Bearer "+token))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
        mockMvc.perform(get("/cart").header("Authorization", "Bearer invalid")
                .cookie(new Cookie("guest_cart", cookie))).andExpect(status().isUnauthorized());
    }
    @Test
    void concurrentMergeConsumesOneGuestSourceOnlyOnce() throws Exception {
        UUID variant = fixture();
        String proof = mockMvc.perform(get("/cart")).andReturn().getResponse().getHeader("Set-Cookie")
                .split(";", 2)[0].split("=", 2)[1];
        mockMvc.perform(put("/cart/lines/" + variant).cookie(new Cookie("guest_cart", proof))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        UUID member = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) "
                + "VALUES (?,?,?,'MEMBER','ACTIVE','Member')", member, "member-"+member+"@example.test", "test-hash");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return mergePort.merge(new GuestCartMergeCommand(actor(member,"MEMBER"), proof)); });
            var two = pool.submit(() -> { start.await(); return mergePort.merge(new GuestCartMergeCommand(actor(member,"MEMBER"), proof)); });
            start.countDown();
            var a = one.get(20, TimeUnit.SECONDS);
            var b = two.get(20, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThat((a.merged() ? 1 : 0) + (b.merged() ? 1 : 0)).isEqualTo(1);
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT quantity FROM marketplace.cart_line l JOIN marketplace.cart c ON c.id=l.cart_id "
                        + "WHERE c.member_id=? AND l.variant_id=?", Integer.class, member, variant)).isEqualTo(2);
    }
    @Test
    void mergeReportsOverStockLineWithoutChangingMemberQuantity() throws Exception {
        UUID variant = fixture();
        String proof = mockMvc.perform(get("/cart")).andReturn().getResponse().getHeader("Set-Cookie")
                .split(";", 2)[0].split("=", 2)[1];
        mockMvc.perform(put("/cart/lines/" + variant).cookie(new Cookie("guest_cart", proof))
                .header("Origin", "https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        UUID member = UUID.randomUUID(), cart = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) "
                + "VALUES (?,?,?,'MEMBER','ACTIVE','Member')", member, "member-"+member+"@example.test", "test-hash");
        jdbc.update("INSERT INTO marketplace.cart(id,member_id) VALUES (?,?)",cart,member);
        jdbc.update("INSERT INTO marketplace.cart_line(cart_id,variant_id,quantity) VALUES (?,?,6)",cart,variant);
        var result = mergePort.merge(new GuestCartMergeCommand(actor(member,"MEMBER"), proof));
        org.assertj.core.api.Assertions.assertThat(result.merged()).isTrue();
        org.assertj.core.api.Assertions.assertThat(result.rejectedLines()).singleElement()
                .satisfies(line -> {
                    org.assertj.core.api.Assertions.assertThat(line.variantId()).isEqualTo(variant);
                    org.assertj.core.api.Assertions.assertThat(line.code()).isEqualTo("QUANTITY_EXCEEDED");
                });
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT quantity FROM marketplace.cart_line WHERE cart_id=? AND variant_id=?",Integer.class,cart,variant))
                .isEqualTo(6);
    }
    @Test
    void directMergePortRejectsSellerBeforeReadingGuestSource() throws Exception {
        fixture();
        UUID seller = jdbc.queryForObject("SELECT id FROM marketplace.account WHERE role='SELLER' ORDER BY id DESC LIMIT 1",
                UUID.class);
        String proof = mockMvc.perform(get("/cart")).andReturn().getResponse().getHeader("Set-Cookie")
                .split(";", 2)[0].split("=", 2)[1];
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mergePort.merge(new GuestCartMergeCommand(actor(seller,"SELLER"), proof)))
                .isInstanceOf(com.phucle.ecommerce.cart.domain.CartFailure.class)
                .hasMessage("ROLE_DENIED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mergePort.merge(new GuestCartMergeCommand(actor(UUID.randomUUID(),"ADMIN"), proof)))
                .isInstanceOf(com.phucle.ecommerce.cart.domain.CartFailure.class)
                .hasMessage("ROLE_DENIED");
    }
    @Test
    void storedLineRemainsVisibleAfterOfferIsUnpublished() throws Exception {
        UUID variant=fixture();
        String proof=mockMvc.perform(get("/cart")).andReturn().getResponse().getHeader("Set-Cookie")
                .split(";",2)[0].split("=",2)[1];
        mockMvc.perform(put("/cart/lines/"+variant).cookie(new Cookie("guest_cart",proof))
                .header("Origin","https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE marketplace.product SET published=false WHERE id=(SELECT product_id FROM marketplace.variant WHERE id=?)",
                variant);
        mockMvc.perform(get("/cart").cookie(new Cookie("guest_cart",proof)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].variantId").value(variant.toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.totalUsd").value("25.00"));
        mockMvc.perform(put("/cart/lines/"+variant).cookie(new Cookie("guest_cart",proof))
                .header("Origin","https://shop.example.test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":3,\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_UNAVAILABLE"));
    }
    @Test
    void memberCanSetAndRemoveOwnLineWithoutGuestOrigin() throws Exception {
        UUID variant = fixture();
        String login = "member-"+UUID.randomUUID()+"@example.test";
        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\""+login+"\",\"password\":\"long-test-password\",\"displayName\":\"Member\"}"))
                .andExpect(status().isCreated());
        String token = json.readTree(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\""+login+"\",\"password\":\"long-test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(put("/cart/lines/"+variant).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(delete("/cart/lines/"+variant).header("Authorization","Bearer "+token)
                .param("expectedVersion","1")).andExpect(status().isNoContent());
        mockMvc.perform(get("/cart").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.lines").isEmpty());
    }
    private UUID fixture() {
        UUID seller = UUID.randomUUID(), shop = UUID.randomUUID(), category = UUID.randomUUID();
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) VALUES (?,?,?,'SELLER','ACTIVE','Seller')",
                seller, "seller-"+seller+"@example.test", "test-hash");
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES (?,?,?,'ACTIVE')",shop,seller,"Test shop");
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?,?)",category,"Test category");
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,?,?,?,?,true)",
                product,shop,category,"Test product","Description");
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,?,?,12.50)",
                variant,product,"sku-"+variant,"Variant");
        jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand) VALUES (?,7)",variant);
        return variant;
    }
    private TrustedActor actor(UUID id,String role) { return new TrustedActor(id,role,"test"); }
}

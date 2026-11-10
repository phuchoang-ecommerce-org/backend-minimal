package com.phucle.ecommerce.discovery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.JwtTestKeys;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class DiscoveryHttpIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("discovery_test").withPassword("discovery_test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("DB_URL", DB::getJdbcUrl);
        r.add("DB_RUNTIME_USER", DB::getUsername);
        r.add("DB_RUNTIME_PASSWORD", DB::getPassword);
        r.add("DB_MIGRATION_USER", DB::getUsername);
        r.add("DB_MIGRATION_PASSWORD", DB::getPassword);
        r.add("JWT_PRIVATE_KEY", JwtTestKeys::privateKey);
        r.add("JWT_PUBLIC_KEY", JwtTestKeys::publicKey);
        r.add("CURSOR_MAC_SECRET", () -> "test-only-cursor-mac-secret-at-least-32-bytes");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    static final UUID CATEGORY = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID VISIBLE = UUID.fromString("50000000-0000-0000-0000-000000000001");
    static final UUID HIDDEN = UUID.fromString("50000000-0000-0000-0000-000000000002");
    static final UUID INACTIVE = UUID.fromString("50000000-0000-0000-0000-000000000003");
    static final UUID VARIANT = UUID.fromString("60000000-0000-0000-0000-000000000001");
    @BeforeAll static void seed(@Autowired JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO marketplace.account(id,normalized_login,password_hash,role,status,display_name) VALUES ('20000000-0000-0000-0000-000000000001','discovery1@example.test','x','SELLER','ACTIVE','Seller One'),('20000000-0000-0000-0000-000000000002','discovery2@example.test','x','SELLER','ACTIVE','Seller Two')");
        jdbc.update("INSERT INTO marketplace.seller_shop(id,account_id,name,status) VALUES ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','Active','ACTIVE'),('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','Inactive','SUSPENDED')");
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?, 'Books')", CATEGORY);
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,'30000000-0000-0000-0000-000000000001',?,'Synthetic Java Book','Learn Java',true),(?,'30000000-0000-0000-0000-000000000001',?,'Hidden Book','Hidden',false),(?,'30000000-0000-0000-0000-000000000002',?,'Inactive Book','Inactive',true)", VISIBLE,CATEGORY,HIDDEN,CATEGORY,INACTIVE,CATEGORY);
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,'SYN-JAVA','Paperback',12.50)",VARIANT,VISIBLE);
        jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand) VALUES (?,7)",VARIANT);
    }
    @Test void categoriesAndVisibleProductDetails() throws Exception {
        mvc.perform(get("/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Books"));
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].variants[0].id").value(VARIANT.toString()))
                .andExpect(jsonPath("$.items[0].variants[0].priceUsd").value("12.50"))
                .andExpect(jsonPath("$.items[0].variants[0].stock.onHand").value(7));
        mvc.perform(get("/products/{id}",VISIBLE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0)).andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.ratingAverage").value((Object)null));
        mvc.perform(get("/products/{id}",HIDDEN)).andExpect(status().isNotFound());
        mvc.perform(get("/products/{id}",INACTIVE)).andExpect(status().isNotFound());
    }
    @Test void filtersAndInvalidCursor() throws Exception {
        mvc.perform(get("/products").param("q","Java").param("categoryId",CATEGORY.toString()).param("minPrice","12.50").param("maxPrice","12.50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/products").param("q","absent")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/products").param("cursor","tampered")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/products").param("limit","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("minPrice","13.00").param("maxPrice","12.00")).andExpect(status().isBadRequest());
    }
    @Test void categoryCursorIsBoundedAndCannotCrossEndpointsOrFilters() throws Exception {
        UUID second = UUID.fromString("10000000-0000-0000-0000-000000000002");
        jdbc.update("INSERT INTO marketplace.category(id,name) VALUES (?, 'Another')", second);
        try {
            String first = mvc.perform(get("/categories").param("limit","1")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true)).andReturn().getResponse().getContentAsString();
            String cursor = json.readTree(first).get("nextCursor").asText();
            mvc.perform(get("/categories").param("limit","1").param("cursor",cursor)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(CATEGORY.toString()))
                    .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.nextCursor").value((Object)null));
            mvc.perform(get("/products").param("cursor",cursor)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            String tampered = cursor.substring(0,cursor.length()-1) + (cursor.endsWith("A") ? "B" : "A");
            mvc.perform(get("/categories").param("cursor",tampered)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        } finally { jdbc.update("DELETE FROM marketplace.category WHERE id=?", second); }
    }
    @Test void productCursorRejectsChangedFilter() throws Exception {
        UUID second = UUID.fromString("50000000-0000-0000-0000-000000000004");
        UUID variant = UUID.fromString("60000000-0000-0000-0000-000000000004");
        jdbc.update("INSERT INTO marketplace.product(id,seller_id,category_id,title,description,published) VALUES (?,'30000000-0000-0000-0000-000000000001',?,'Synthetic Java Companion','Learn more Java',true)",second,CATEGORY);
        jdbc.update("INSERT INTO marketplace.variant(id,product_id,sku,label,price_usd) VALUES (?,?,'SYN-JAVA-2','Digital',15.00)",variant,second);
        jdbc.update("INSERT INTO marketplace.stock(variant_id,on_hand) VALUES (?,3)",variant);
        try {
            String first = mvc.perform(get("/products").param("q","Java").param("limit","1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = json.readTree(first).get("nextCursor").asText();
            mvc.perform(get("/products").param("q","Java").param("limit","1").param("cursor",cursor))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(VISIBLE.toString()));
            mvc.perform(get("/products").param("q","Book").param("cursor",cursor))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        } finally {
            jdbc.update("DELETE FROM marketplace.stock WHERE variant_id=?",variant);
            jdbc.update("DELETE FROM marketplace.variant WHERE id=?",variant);
            jdbc.update("DELETE FROM marketplace.product WHERE id=?",second);
        }
    }
}

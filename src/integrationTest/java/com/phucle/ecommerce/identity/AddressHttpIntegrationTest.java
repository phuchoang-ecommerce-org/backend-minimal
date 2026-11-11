package com.phucle.ecommerce.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phucle.ecommerce.MarketplaceApplication;
import com.phucle.ecommerce.identity.api.OwnedAddressQueries;
import com.phucle.ecommerce.identity.api.TrustedActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
class AddressHttpIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace").withUsername("address_test").withPassword("address_test");
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
    @Autowired JwtDecoder decoder;
    @Autowired OwnedAddressQueries ownedAddresses;

    @Test void memberCreatesAndListsOnlyOwnAddresses() throws Exception {
        String own = member();
        String foreign = member();
        create(own, "Own");
        create(foreign, "Foreign");
        mvc.perform(get("/me/addresses").header("Authorization", own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].recipient").value("Own"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void paginatesWithActorBoundCursorAndRejectsTampering() throws Exception {
        String own = member(); String other = member();
        create(own, "First"); create(own, "Second"); create(other, "Other");
        String body = mvc.perform(get("/me/addresses?limit=1").header("Authorization", own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true)).andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.nextCursor");
        mvc.perform(get("/me/addresses").param("limit", "1").param("cursor", cursor).header("Authorization", own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/me/addresses").param("cursor", cursor).header("Authorization", other))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/me/addresses").param("cursor", cursor + "x").header("Authorization", own))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/me/addresses?limit=101").header("Authorization", own))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test void rejectsInvalidAddressAndOwnerOverrideWithoutInsert() throws Exception {
        String own = member();
        UUID ownerId = UUID.randomUUID();
        // The owner field is never part of AddressValue and must be rejected by Jackson.
        mvc.perform(post("/me/addresses").header("Authorization", own).contentType(MediaType.APPLICATION_JSON)
                .content(value("Valid").replaceFirst("}$", ",\"memberId\":\"" + ownerId + "\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/me/addresses").header("Authorization", own).contentType(MediaType.APPLICATION_JSON)
                .content(value(" ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/me/addresses").header("Authorization", own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test void requiresMemberBearer() throws Exception {
        mvc.perform(get("/me/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/addresses").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/me/addresses").header("Authorization", actor("SELLER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_DENIED"));
        mvc.perform(get("/me/addresses").header("Authorization", actor("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test void ownedLookupHidesForeignAndMissingIdsForCheckout() throws Exception {
        String owner = member(); String other = member();
        String response = mvc.perform(post("/me/addresses").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(value("Checkout")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID addressId = UUID.fromString(JsonPath.read(response, "$.id"));
        var ownerActor = trusted(owner);
        var otherActor = trusted(other);
        org.assertj.core.api.Assertions.assertThat(ownedAddresses.findOwned(ownerActor, addressId).recipient())
                .isEqualTo("Checkout");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ownedAddresses.findOwned(otherActor, addressId))
                .isInstanceOf(com.phucle.ecommerce.identity.application.OwnedAddressNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ownedAddresses.findOwned(ownerActor, UUID.randomUUID()))
                .isInstanceOf(com.phucle.ecommerce.identity.application.OwnedAddressNotFoundException.class);
    }
    private TrustedActor trusted(String bearer) {
        UUID id = UUID.fromString(decoder.decode(bearer.substring(7)).getSubject());
        return new TrustedActor(id, "MEMBER", "test");
    }

    private void create(String bearer, String name) throws Exception {
        mvc.perform(post("/me/addresses").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(value(name)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.recipient").value(name));
    }
    private String value(String name) {
        return "{\"recipient\":\"" + name + "\",\"addressLine\":\"1 Main St\",\"locality\":\"Hanoi\",\"postalCode\":\"10000\",\"country\":\"VN\"}";
    }
    private String member() { return actor("MEMBER"); }
    private String actor(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.account (id, normalized_login, password_hash, display_name, role, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                id, id + "@example.test", "test-only-hash", "Test", role);
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("marketplace").subject(id.toString())
                .audience(List.of("marketplace-api")).issuedAt(now).expiresAt(now.plusSeconds(60))
                .claim("roles", List.of(role)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}

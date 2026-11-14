package com.phucle.ecommerce.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phucle.ecommerce.MarketplaceApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
class IdentityHttpIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace")
            .withUsername("identity_test")
            .withPassword("identity_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_RUNTIME_USER", POSTGRES::getUsername);
        registry.add("DB_RUNTIME_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_MIGRATION_USER", POSTGRES::getUsername);
        registry.add("DB_MIGRATION_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_PRIVATE_KEY", JwtTestKeys::privateKey);
        registry.add("JWT_PUBLIC_KEY", JwtTestKeys::publicKey);
        registry.add("CURSOR_MAC_SECRET", () -> "test-only-cursor-mac-secret-at-least-32-bytes");
    }

    @Test
    void registersMemberAndNeverReturnsCredentialMaterial() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("member-" + UUID.randomUUID() + "@example.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.login").isNotEmpty())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Member"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsDuplicateRegistrationWithStableProblemCode() throws Exception {
        String login = "member-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(registration(login)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(registration(login)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LOGIN_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void logsInWithPersistedCredentialsAndIssuesShortLivedBearer() throws Exception {
        String login = "member-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(registration(login)))
                .andExpect(status().isCreated());

        String token = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"long-test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.account.login").value(login))
                .andExpect(jsonPath("$.account.role").value("MEMBER"))
                .andExpect(jsonPath("$.account.displayName").value("Member"))
                .andExpect(jsonPath("$.cartMerge.merged").value(false))
                .andExpect(jsonPath("$.cartMerge.rejectedLines").isEmpty())
                .andReturn().getResponse().getContentAsString()
                .replaceFirst(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Jwt decoded = jwtDecoder.decode(token);
        org.assertj.core.api.Assertions.assertThat(decoded.getSubject()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(decoded.getClaimAsStringList("roles")).containsExactly("MEMBER");
    }

    @Test
    void rejectsInvalidCredentialsAndMalformedBearerWithProblemJson() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"absent@example.test\",\"password\":\"long-test-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String wrongIssuer = signedToken("other-issuer", "marketplace-api", java.time.Instant.now().plusSeconds(60));
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String wrongAudience = signedToken("marketplace", "other-audience", java.time.Instant.now().plusSeconds(60));
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String expired = signedToken("marketplace", "marketplace-api", java.time.Instant.now().minusSeconds(120));
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsTamperedRoleAndInactiveAccountTokens() throws Exception {
        String login = "member-" + UUID.randomUUID() + "@example.test";
        String accountId = mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(login))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()
                .replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String memberToken = signedTokenFor(accountId, "MEMBER");
        int signatureStart = memberToken.lastIndexOf('.') + 1;
        String tampered = memberToken.substring(0, signatureStart)
                + (memberToken.charAt(signatureStart) == 'A' ? "B" : "A")
                + memberToken.substring(signatureStart + 1);
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + signedTokenFor(accountId, "ADMIN")))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE marketplace.account SET status = 'SUSPENDED' WHERE id = ?", UUID.fromString(accountId));
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isUnauthorized());
    }

    private String signedTokenFor(String accountId, String role) {
        var now = java.time.Instant.now();
        var claims = JwtClaimsSet.builder().issuer("marketplace").subject(accountId)
                .audience(java.util.List.of("marketplace-api")).issuedAt(now)
                .expiresAt(now.plusSeconds(60)).claim("roles", java.util.List.of(role)).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private String registration(String login) {
        return "{\"login\":\"" + login + "\",\"password\":\"long-test-password\",\"displayName\":\"Member\"}";
    }

    private String signedToken(String issuer, String audience, java.time.Instant expiresAt) {
        java.time.Instant issuedAt = expiresAt.isBefore(java.time.Instant.now())
                ? expiresAt.minusSeconds(60)
                : java.time.Instant.now().minusSeconds(1);
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .audience(java.util.List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", java.util.List.of("MEMBER"))
                .build();
        var headers = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}

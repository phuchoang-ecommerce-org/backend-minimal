package com.phucle.ecommerce.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FoundationContractTest {

    @Test
    void profileAndFixturePolicyIsProductionSafe() throws IOException {
        String production = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        String development = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(production).contains("enabled: false");
        assertThat(production).contains("quick-login:");
        assertThat(development).contains("DEV_FIXTURES_ENABLED:false");
        assertThat(development).doesNotContain("password:");
    }

    @Test
    void composeUsesEnvironmentSuppliedCredentialsAndPostgres18() throws IOException {
        String compose = Files.readString(Path.of("compose.yaml"));

        assertThat(compose).contains("postgres:18");
        assertThat(compose).contains("${DB_PASSWORD:?set local DB_PASSWORD}");
        assertThat(compose).doesNotContain("POSTGRES_PASSWORD=secret");
    }

    @Test
    void productionRejectsDevFixturesAndFixtureIdsKeepAccountAndShopDistinct() {
        assertThatIllegalStateException().isThrownBy(
                () -> ProfilePolicy.requireValid(new String[] {"prod"}, new String[0], true));
        assertThatIllegalStateException().isThrownBy(
                () -> ProfilePolicy.requireValid(new String[] {"dev", "prod"}, new String[0], false));
        assertThat(DevFixtureSeedConfiguration.SELLER_ACCOUNT_ID)
                .isNotEqualTo(DevFixtureSeedConfiguration.SELLER_SHOP_ID);
    }
}

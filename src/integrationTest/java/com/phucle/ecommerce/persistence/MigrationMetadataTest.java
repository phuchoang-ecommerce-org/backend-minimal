package com.phucle.ecommerce.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Testcontainers(disabledWithoutDocker = true)
class MigrationMetadataTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("marketplace")
            .withUsername("migration_test")
            .withPassword("migration_test");

    @Test
    void v1MigrationUsesOnlyMarketplaceSchema() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V1__marketplace.sql"));

        assertThat(migration).contains("CREATE SCHEMA IF NOT EXISTS marketplace");
        assertThat(migration).contains("CREATE TABLE marketplace.account");
        assertThat(migration).contains("CREATE TABLE marketplace.audit_entry");
        assertThat(migration).doesNotContain("public.");
    }

    @Test
    void appliesAllMigrationsToAnEmptyPostgresDatabaseAndRepeatsWithoutChanges() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("marketplace")
                .defaultSchema("marketplace")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'marketplace'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class)).isEqualTo(19);
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM marketplace.audit_entry"))
                .hasMessageContaining("audit_entry is append-only");
    }
}

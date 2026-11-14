package com.phucle.ecommerce.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;

class DevProfileEnvironmentTest {

    @TempDir Path temporaryDirectory;

    @Test
    void devProfileLoadsLocalEnvironmentFile() throws Exception {
        Path envFile = temporaryDirectory.resolve("local.env");
        Files.writeString(envFile, "DB_URL=jdbc:postgresql://localhost:5432/dev_profile_test\n");

        SpringApplication application = new SpringApplication(ConfigOnly.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("dev");
        application.setDefaultProperties(Map.of("MARKETPLACE_ENV_FILE", envFile.toString()));

        try (var context = application.run()) {
            assertThat(context.getEnvironment().getProperty("DB_URL"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/dev_profile_test");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfigOnly {
    }
}

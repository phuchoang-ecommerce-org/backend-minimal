package com.phucle.ecommerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FoundationArchitecturePolicyTest {

    @Test
    void declaresAllTwelveExplicitModulesAndNoUnsupportedStoreOrTransport() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/phucle/ecommerce"))) {
            long moduleDeclarations = paths
                    .filter(path -> path.getFileName().toString().equals("package-info.java"))
                    .filter(this::isModuleDeclaration)
                    .count();

            assertThat(moduleDeclarations).isEqualTo(12);
        }

        String build = Files.readString(Path.of("build.gradle.kts"));
        assertThat(build)
                .doesNotContain("spring-boot-starter-data-jpa")
                .doesNotContain("spring-boot-starter-webflux")
                .doesNotContain("spring-kafka")
                .doesNotContain("spring-data-redis");
    }

    private boolean isModuleDeclaration(Path path) {
        try {
            return Files.readString(path).contains("@org.springframework.modulith.ApplicationModule");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

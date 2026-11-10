package com.phucle.ecommerce.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DiscoveryBoundaryTest {
    @Test void exportedApiDoesNotDependOnServletTypes() {
        noClasses().that().resideInAPackage("com.phucle.ecommerce..api..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "org.springframework.web..").check(
                        new ClassFileImporter().importPackages("com.phucle.ecommerce"));
    }
    @Test void discoveryDoesNotOwnCrossModuleSql() throws Exception {
        Path source = Path.of("src/main/java/com/phucle/ecommerce/discovery");
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString())
                        .doesNotContain("marketplace.product", "marketplace.category", "marketplace.variant",
                                "marketplace.stock", "marketplace.review", "marketplace.seller_shop");
            }
        }
    }
}

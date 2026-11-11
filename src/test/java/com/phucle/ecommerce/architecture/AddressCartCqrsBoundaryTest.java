package com.phucle.ecommerce.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.phucle.ecommerce.cart.application.CartQueryService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class AddressCartCqrsBoundaryTest {
    @Test void addressWebDoesNotReachDomainOrJdbc() {
        noClasses().that().resideInAPackage("..identity.adapter.in.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..identity.domain..", "..identity.adapter.out.jdbc..")
                .check(new ClassFileImporter().importPackages("com.phucle.ecommerce.identity.adapter.in.web"));
    }
    @Test void addressApplicationDoesNotReachJdbc() {
        noClasses().that().resideInAPackage("..identity.application..")
                .should().dependOnClassesThat().resideInAnyPackage("..identity.adapter.out.jdbc..", "org.springframework.jdbc..")
                .check(new ClassFileImporter().importPackages("com.phucle.ecommerce.identity.application"));
    }
    @Test void cartQueryDoesNotLoadAggregate() {
        org.assertj.core.api.Assertions.assertThat(CartQueryService.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().endsWith("CartRepositoryPort"));
        noClasses().that().haveSimpleName("CartQueryService")
                .should().dependOnClassesThat().resideInAPackage("..cart.domain..")
                .check(new ClassFileImporter().importClasses(CartQueryService.class));
    }
}

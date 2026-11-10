package com.phucle.ecommerce.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.phucle.ecommerce.identity.domain.Account;
import com.phucle.ecommerce.identity.domain.AccountId;
import com.phucle.ecommerce.identity.domain.NormalizedLogin;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;

class IdentityArchitectureTest {
    @Test
    void identityDomainHasNoFrameworkDependency() {
        noClasses().that().resideInAPackage("..identity.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..").check(
                        new ClassFileImporter().importPackages("com.phucle.ecommerce.identity.domain"));
    }

    @Test
    void aggregateAndValuesCarryJmoleculesMarkers() {
        org.assertj.core.api.Assertions.assertThat(Account.class).hasAnnotation(AggregateRoot.class);
        org.assertj.core.api.Assertions.assertThat(AccountId.class).hasAnnotation(ValueObject.class);
        org.assertj.core.api.Assertions.assertThat(NormalizedLogin.class).hasAnnotation(ValueObject.class);
    }
}

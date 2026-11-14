import org.gradle.api.tasks.testing.Test

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.phucle"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springModulithVersion = "1.4.13"
val mapstructVersion = "1.6.3"
val lombokVersion = "1.18.36"
val archUnitVersion = "1.4.1"
val jMoleculesVersion = "1.10.0"
val testcontainersVersion = "1.20.4"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.jmolecules:jmolecules-ddd:$jMoleculesVersion")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")

    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archUnitVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
    }
}

sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
    create("checkoutRed") {
        java.srcDir("src/checkoutRed/java")
        resources.srcDir("src/checkoutRed/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets["integrationTest"].output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
    named("checkoutRedImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("checkoutRedRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    doLast {
        val xmlReports = reports.junitXml.outputLocation.get().asFile
            .walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
            .toList()
        val testCount = xmlReports.sumOf { report ->
            Regex("tests=\\\"(\\d+)\\\"").find(report.readText())?.groupValues?.get(1)?.toInt() ?: 0
        }
        val skippedCount = xmlReports.sumOf { report ->
            Regex("skipped=\\\"(\\d+)\\\"").find(report.readText())?.groupValues?.get(1)?.toInt() ?: 0
        }
        check(testCount > 0) { "${path} discovered zero tests" }
        check(skippedCount == 0) { "${path} skipped $skippedCount tests" }
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs isolated PostgreSQL integration-contract tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}

tasks.register<Test>("checkoutRed") {
    description = "Runs PB-006 checkout and cross-flow regression contracts."
    group = "verification"
    testClassesDirs = sourceSets["checkoutRed"].output.classesDirs
    classpath = sourceSets["checkoutRed"].runtimeClasspath
    shouldRunAfter(integrationTest)
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(integrationTest)
    dependsOn("checkoutRed")
}

val assertNoCheckoutPlaceholder = tasks.register("assertNoCheckoutPlaceholder") {
    description = "Blocks application packaging while checkout compile seams remain unimplemented."
    group = "verification"
    doLast {
        val seams = listOf(
            file("src/main/java/com/phucle/ecommerce/ordering/application/PlaceOrder.java"),
            file("src/main/java/com/phucle/ecommerce/ordering/domain/OrderFactory.java")
        )
        check(seams.none { it.exists() && it.readText().contains("PB-006 checkout awaits Sprint 2 implementation") }) {
            "PB-006 checkout placeholder remains; replace it before packaging the application"
        }
    }
}

tasks.named("jar") { dependsOn(assertNoCheckoutPlaceholder) }
tasks.named("bootJar") { dependsOn(assertNoCheckoutPlaceholder) }

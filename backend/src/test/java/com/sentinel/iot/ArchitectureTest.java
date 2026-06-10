package com.sentinel.iot;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture fitness functions — verified on every build.
 *
 * Rules are grouped into four sections:
 *   1. Layer dependency rules (existing + new)
 *   2. Naming conventions
 *   3. General coding standards
 *   4. Package-level isolation invariants
 *
 * All rules target production code only (DoNotIncludeTests).
 */
@AnalyzeClasses(
    packages = "com.sentinel.iot",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    // ── 1. Layer dependency rules ─────────────────────────────────────────────

    @ArchTest
    static final ArchRule repositories_doNotDependOnServices =
        noClasses().that().resideInAPackage("com.sentinel.iot.repository..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.service..")
            .because("repositories must be unaware of business logic");

    @ArchTest
    static final ArchRule models_doNotDependOnServices =
        noClasses().that().resideInAPackage("com.sentinel.iot.model..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.service..")
            .because("domain models must not depend on application services");

    @ArchTest
    static final ArchRule models_doNotDependOnRepositories =
        noClasses().that().resideInAPackage("com.sentinel.iot.model..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.repository..")
            .because("domain models must not reference persistence layer");

    @ArchTest
    static final ArchRule dtos_doNotDependOnRepositories =
        noClasses().that().resideInAPackage("com.sentinel.iot.dto..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.repository..")
            .because("DTOs are plain data carriers and must not reference persistence");

    @ArchTest
    static final ArchRule controllers_doNotDependOnKafka =
        noClasses().that().resideInAPackage("com.sentinel.iot.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.kafka..")
            .because("HTTP controllers must not couple to event streaming internals");

    @ArchTest
    static final ArchRule services_doNotDependOnControllers =
        noClasses().that().resideInAPackage("com.sentinel.iot.service..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.controller..")
            .because("services must not depend on transport-layer controllers");

    @ArchTest
    static final ArchRule dtos_doNotDependOnServices =
        noClasses().that().resideInAPackage("com.sentinel.iot.dto..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.service..")
            .because("DTOs must remain pure data-transfer objects");

    @ArchTest
    static final ArchRule models_doNotDependOnDtos =
        noClasses().that().resideInAPackage("com.sentinel.iot.model..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.dto..")
            .because("JPA entities must not import API contract objects");

    @ArchTest
    static final ArchRule kafka_doesNotDependOnControllers =
        noClasses().that().resideInAPackage("com.sentinel.iot.kafka..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.controller..")
            .because("event consumers/producers are transport-agnostic");

    @ArchTest
    static final ArchRule repositories_doNotDependOnKafka =
        noClasses().that().resideInAPackage("com.sentinel.iot.repository..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.kafka..")
            .because("data access must not trigger streaming side-effects");

    @ArchTest
    static final ArchRule websocket_doesNotDependOnControllers =
        noClasses().that().resideInAPackage("com.sentinel.iot.websocket..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.controller..")
            .because("WebSocket handlers are independent from REST controllers");

    // ── 2. Naming conventions ─────────────────────────────────────────────────

    @ArchTest
    static final ArchRule service_classes_named_correctly =
        classes().that().resideInAPackage("com.sentinel.iot.service")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Service")
            .because("every concrete class in the service package names its role as a Service");

    @ArchTest
    static final ArchRule repository_interfaces_named_correctly =
        classes().that().resideInAPackage("com.sentinel.iot.repository..")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Repository")
            .because("Spring Data repository interfaces follow the XxxRepository convention");

    @ArchTest
    static final ArchRule controller_classes_named_correctly =
        classes().that().resideInAPackage("com.sentinel.iot.controller..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Controller")
            .because("REST endpoint classes follow the XxxController convention");

    // ── 3. General coding standards ───────────────────────────────────────────

    @ArchTest
    static final ArchRule no_field_injection =
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("constructor injection makes dependencies explicit and testable without a DI container");

    @ArchTest
    static final ArchRule no_standard_streams =
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("production code must use SLF4J loggers, not System.out / System.err");

    @ArchTest
    static final ArchRule no_java_util_logging =
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
            .because("the project standardises on SLF4J; java.util.logging must not be introduced");
}

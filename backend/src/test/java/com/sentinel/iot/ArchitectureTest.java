package com.sentinel.iot;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.sentinel.iot")
class ArchitectureTest {

    @ArchTest
    static final ArchRule repositories_doNotDependOnServices =
        noClasses().that().resideInAPackage("com.sentinel.iot.repository..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.service..");

    @ArchTest
    static final ArchRule models_doNotDependOnServices =
        noClasses().that().resideInAPackage("com.sentinel.iot.model..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.service..");

    @ArchTest
    static final ArchRule models_doNotDependOnRepositories =
        noClasses().that().resideInAPackage("com.sentinel.iot.model..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.repository..");

    @ArchTest
    static final ArchRule dtos_doNotDependOnRepositories =
        noClasses().that().resideInAPackage("com.sentinel.iot.dto..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.repository..");

    @ArchTest
    static final ArchRule controllers_doNotDependOnKafka =
        noClasses().that().resideInAPackage("com.sentinel.iot.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.kafka..");

    @ArchTest
    static final ArchRule services_doNotDependOnControllers =
        noClasses().that().resideInAPackage("com.sentinel.iot.service..")
            .should().dependOnClassesThat().resideInAPackage("com.sentinel.iot.controller..");
}

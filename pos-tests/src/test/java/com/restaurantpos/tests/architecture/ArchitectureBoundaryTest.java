package com.restaurantpos.tests.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces modular monolith architectural boundaries across all POS modules.
 */
class ArchitectureBoundaryTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.restaurantpos");

    @Test
    void domainMustRemainPureWithoutFrameworkOrInfrastructureDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.restaurantpos.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javafx..",
                        "com.restaurantpos.infrastructure..",
                        "com.restaurantpos.application..",
                        "com.restaurantpos.api..",
                        "com.restaurantpos.desktop.."
                );
        rule.check(ALL_CLASSES);
    }

    @Test
    void applicationMustNotDependOnInfrastructureApiOrUi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.restaurantpos.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javafx..",
                        "com.restaurantpos.infrastructure..",
                        "com.restaurantpos.api..",
                        "com.restaurantpos.desktop.."
                );
        rule.check(ALL_CLASSES);
    }

    @Test
    void apiMustNotBypassApplicationIntoInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.restaurantpos.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.restaurantpos.infrastructure..",
                        "javafx..",
                        "com.restaurantpos.desktop.."
                );
        rule.check(ALL_CLASSES);
    }

    @Test
    void desktopMustNeverDependOnInfrastructureOrSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.restaurantpos.desktop..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.restaurantpos.infrastructure..",
                        "com.restaurantpos.api.."
                );
        rule.check(ALL_CLASSES);
    }
}

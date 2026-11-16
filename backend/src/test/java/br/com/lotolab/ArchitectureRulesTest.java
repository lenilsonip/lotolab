package br.com.lotolab;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "br.com.lotolab", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule domain_is_framework_and_adapter_independent = noClasses()
            .that().resideInAPackage("br.com.lotolab.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.postgresql..", "java.sql..", "jakarta..", "javax..",
                    "br.com.lotolab.api..", "br.com.lotolab.infrastructure..",
                    "br.com.lotolab.application..", "br.com.lotolab.contests..");

    @ArchTest
    static final ArchRule contest_application_does_not_depend_on_adapters_or_spring = noClasses()
            .that().resideInAPackage("br.com.lotolab.contests.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "br.com.lotolab.api..", "br.com.lotolab.infrastructure..",
                    "br.com.lotolab.application..", "br.com.lotolab.contests.adapter..",
                    "br.com.lotolab.contests.bootstrap..");

    @ArchTest
    static final ArchRule contest_ports_are_framework_and_adapter_independent = noClasses()
            .that().resideInAnyPackage("br.com.lotolab.contests.application.port.in..",
                    "br.com.lotolab.contests.application.port.out..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.postgresql..", "java.sql..", "jakarta..", "javax..",
                    "br.com.lotolab.api..", "br.com.lotolab.infrastructure..",
                    "br.com.lotolab.contests.adapter..", "br.com.lotolab.contests.bootstrap..");

    @ArchTest
    static final ArchRule contest_import_application_does_not_depend_on_adapters_or_spring = noClasses()
            .that().resideInAPackage("br.com.lotolab.application.importing..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "br.com.lotolab.api..", "br.com.lotolab.infrastructure..");

    @ArchTest
    static final ArchRule contest_web_adapter_does_not_depend_on_output_adapters = noClasses()
            .that().resideInAPackage("br.com.lotolab.contests.adapter.in.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.lotolab.infrastructure..", "br.com.lotolab.contests.application.port.out..");

    @ArchTest
    static final ArchRule contest_input_adapters_do_not_depend_on_output_adapters = noClasses()
            .that().resideInAPackage("br.com.lotolab.contests.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("br.com.lotolab.contests.adapter.out..");

    @ArchTest
    static final ArchRule contest_application_and_ports_do_not_depend_on_bootstrap = noClasses()
            .that().resideInAnyPackage("br.com.lotolab.contests.application..",
                    "br.com.lotolab.contests.application.port..")
            .should().dependOnClassesThat().resideInAPackage("br.com.lotolab.contests.bootstrap..");

    @ArchTest
    static final ArchRule contest_module_slices_are_acyclic = slices()
            .matching("br.com.lotolab.contests.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule research_domain_is_framework_and_adapter_independent = noClasses()
            .that().resideInAPackage("br.com.lotolab.research.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.postgresql..", "java.sql..", "jakarta..", "javax..",
                    "org.slf4j..", "org.apache.logging..", "br.com.lotolab.infrastructure..",
                    "br.com.lotolab.research.application..", "br.com.lotolab.research.adapter..",
                    "br.com.lotolab.research.bootstrap..", "br.com.lotolab.api..");

    @ArchTest
    static final ArchRule research_application_does_not_depend_on_frameworks_or_adapters = noClasses()
            .that().resideInAPackage("br.com.lotolab.research.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.postgresql..", "java.sql..", "jakarta..", "javax..",
                    "org.slf4j..", "org.apache.logging..", "br.com.lotolab.api..",
                    "br.com.lotolab.infrastructure..", "br.com.lotolab.application..",
                    "br.com.lotolab.research.adapter..", "br.com.lotolab.research.bootstrap..");

    @ArchTest
    static final ArchRule research_input_adapters_use_only_application_input_ports = noClasses()
            .that().resideInAPackage("br.com.lotolab.research.adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "br.com.lotolab.research.application.port.out..",
                    "br.com.lotolab.research.adapter.out..",
                    "br.com.lotolab.infrastructure.persistence..",
                    "br.com.lotolab.infrastructure.agent..",
                    "br.com.lotolab.research.adapter.out.persistence..");

    @ArchTest
    static final ArchRule research_output_adapters_do_not_depend_on_input_ports = noClasses()
            .that().resideInAPackage("br.com.lotolab.research.adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("br.com.lotolab.research.application.port.in..");

    @ArchTest
    static final ArchRule research_bootstrap_is_not_a_dependency_of_inner_layers = noClasses()
            .that().resideInAnyPackage("br.com.lotolab.research.domain..", "br.com.lotolab.research.application..",
                    "br.com.lotolab.research.adapter..")
            .should().dependOnClassesThat().resideInAPackage("br.com.lotolab.research.bootstrap..");

    @ArchTest
    static final ArchRule research_module_slices_are_acyclic = slices()
            .matching("br.com.lotolab.research.(*)..")
            .should().beFreeOfCycles();
}

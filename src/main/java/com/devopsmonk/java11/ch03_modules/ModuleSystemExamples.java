package com.devopsmonk.java11.ch03_modules;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.Optional;
import java.util.Set;

/**
 * Chapter 03 — Module System (JPMS / Project Jigsaw) — JEP 261
 * Tutorial: https://devops-monk.com/tutorials/java11/module-system/
 *
 * The Java Platform Module System (JPMS) was introduced in Java 9.
 * A true multi-module project requires separate source roots and module-info.java
 * files per module, which is beyond a single-class demo. This file instead:
 *   1. Explains the core concepts with inline commentary
 *   2. Introspects the JDK's own modules at runtime via the Module API
 *   3. Shows escape hatches (--add-opens / --add-exports) used during migration
 *
 * To experiment with JPMS hands-on, see the companion module-project/ directory
 * (two modules: com.devopsmonk.api and com.devopsmonk.impl).
 */
public class ModuleSystemExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 03: Module System (JPMS) ===\n");

        conceptsOverview();
        introspectCurrentModule();
        introspectJdkModules();
        moduleDescriptorApi();
        escapeHatches();
    }

    // -------------------------------------------------------------------------
    // 1. Core Concepts
    // -------------------------------------------------------------------------
    static void conceptsOverview() {
        System.out.println("--- 1. Core Concepts ---");

        System.out.println(
            "  A module is a named, self-describing group of packages.\n" +
            "  Declared in module-info.java at the root of the source tree:\n\n" +

            "    // com.devopsmonk.api/src/module-info.java\n" +
            "    module com.devopsmonk.api {\n" +
            "        exports com.devopsmonk.api.model;         // visible to all\n" +
            "        exports com.devopsmonk.api.spi to com.devopsmonk.impl; // targeted\n" +
            "        requires java.net.http;                   // explicit dependency\n" +
            "        requires transitive java.logging;         // re-exported to consumers\n" +
            "        uses com.devopsmonk.api.spi.DataSource;   // SPI consumer\n" +
            "        provides com.devopsmonk.api.spi.DataSource\n" +
            "            with com.devopsmonk.api.internal.PostgresDataSource;\n" +
            "    }\n\n" +

            "  Key keywords:\n" +
            "    exports    — makes a package readable by other modules\n" +
            "    requires   — declares a compile+runtime dependency on another module\n" +
            "    opens      — allows deep reflection (needed by frameworks like Spring)\n" +
            "    uses       — this module uses a service (looks up via ServiceLoader)\n" +
            "    provides   — this module provides a service implementation\n"
        );
    }

    // -------------------------------------------------------------------------
    // 2. Runtime Module API — introspect the module of the running class
    // -------------------------------------------------------------------------
    static void introspectCurrentModule() {
        System.out.println("--- 2. Introspect Current Module ---");

        Module thisModule = ModuleSystemExamples.class.getModule();
        System.out.println("  Module name:    " + thisModule.getName());    // null = unnamed module
        System.out.println("  Is named:       " + thisModule.isNamed());
        System.out.println("  Is open:        " + thisModule.isOpen("com.devopsmonk.java11.ch03_modules"));

        // String is in java.base — a named JDK module
        Module stringModule = String.class.getModule();
        System.out.println("\n  String's module: " + stringModule.getName());
        System.out.println("  String's module exports java.lang: " +
                stringModule.isExported("java.lang"));

        // Check if a package is open for deep reflection
        System.out.println("  java.lang open to unnamed: " +
                stringModule.isOpen("java.lang", thisModule));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Introspect JDK Platform Modules via ModuleFinder
    // -------------------------------------------------------------------------
    static void introspectJdkModules() {
        System.out.println("--- 3. JDK Platform Modules ---");

        // All modules in the boot layer (the JDK itself)
        ModuleLayer bootLayer = ModuleLayer.boot();

        long moduleCount = bootLayer.modules().size();
        System.out.println("  JDK modules loaded in boot layer: " + moduleCount);

        // Find a specific module
        Optional<Module> httpModule = bootLayer.findModule("java.net.http");
        httpModule.ifPresent(m -> {
            System.out.println("  java.net.http found: " + m.getName());
            m.getDescriptor().exports().stream()
             .filter(e -> !e.isQualified())
             .limit(5)
             .forEach(e -> System.out.println("    exports " + e.source()));
        });

        // List some well-known modules
        System.out.println("\n  Well-known JDK modules:");
        String[] known = {"java.base", "java.net.http", "java.sql", "java.xml",
                          "java.logging", "jdk.jfr", "jdk.compiler"};
        for (String name : known) {
            boolean present = bootLayer.findModule(name).isPresent();
            System.out.printf("    %-20s %s%n", name, present ? "✓ present" : "✗ not found");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. ModuleDescriptor API — read module metadata
    // -------------------------------------------------------------------------
    static void moduleDescriptorApi() {
        System.out.println("--- 4. ModuleDescriptor API ---");

        ModuleLayer.boot().findModule("java.net.http").ifPresent(m -> {
            ModuleDescriptor desc = m.getDescriptor();
            System.out.println("  Module: " + desc.name());
            System.out.println("  Version: " + desc.rawVersion().orElse("(none)"));
            System.out.println("  Is open module: " + desc.isOpen());
            System.out.println("  Is automatic: " + desc.isAutomatic());

            System.out.println("  Requires (" + desc.requires().size() + "):");
            desc.requires().stream()
                .limit(5)
                .forEach(r -> System.out.println("    requires " + r.name()));

            System.out.println("  Exports (" + desc.exports().size() + "):");
            desc.exports().stream()
                .filter(e -> !e.isQualified())
                .forEach(e -> System.out.println("    exports " + e.source()));
        });

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Migration Escape Hatches
    //
    // When migrating a Java 8 app to Java 11, frameworks that use deep reflection
    // (Spring, Hibernate, Jackson) may fail with InaccessibleObjectException.
    // --add-opens and --add-exports are the bridge until the library is updated.
    // -------------------------------------------------------------------------
    static void escapeHatches() {
        System.out.println("--- 5. Migration Escape Hatches ---");

        System.out.println(
            "  When reflection breaks with InaccessibleObjectException, add to JVM args:\n\n" +

            "  # Spring / Hibernate commonly need:\n" +
            "  --add-opens java.base/java.lang=ALL-UNNAMED\n" +
            "  --add-opens java.base/java.util=ALL-UNNAMED\n" +
            "  --add-opens java.base/java.lang.reflect=ALL-UNNAMED\n\n" +

            "  # JAXB (removed in Java 11, use jakarta.xml.bind dependency instead):\n" +
            "  # Gradle: implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.0'\n" +
            "  #         runtimeOnly  'com.sun.xml.bind:jaxb-impl:4.0.5'\n\n" +

            "  # Add to Gradle build:\n" +
            "  application {\n" +
            "      applicationDefaultJvmArgs = [\n" +
            "          '--add-opens', 'java.base/java.lang=ALL-UNNAMED',\n" +
            "          '--add-opens', 'java.base/java.util=ALL-UNNAMED'\n" +
            "      ]\n" +
            "  }\n\n" +

            "  Long-term fix: replace internal API usage with public API equivalents.\n" +
            "  Use jdeps --jdk-internals myapp.jar to find all violations upfront.\n"
        );

        // Demonstrate jdeps-equivalent: check if we can access a field reflectively
        try {
            java.lang.reflect.Field f = String.class.getDeclaredField("value");
            f.setAccessible(true);
            System.out.println("  setAccessible succeeded (running with --add-opens or unnamed module)");
        } catch (Exception e) {
            System.out.println("  setAccessible blocked: " + e.getMessage());
            System.out.println("  Fix: add --add-opens java.base/java.lang=ALL-UNNAMED");
        }
    }
}

package com.devopsmonk.java11.ch15_migration;

import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Chapter 15 — Migrating to Java 11: Step-by-Step Guide
 * Tutorial: https://devops-monk.com/tutorials/java11/migration-guide/
 *
 * This chapter demonstrates the practical steps to migrate a Java 8 application
 * to Java 11. Each section shows a concrete before/after and the tooling to use.
 *
 * Migration path: Java 8 → Java 11
 * Key changes:
 *   1. Build tool upgrade (source/target compatibility)
 *   2. Removed Java EE modules → add Jakarta EE dependencies
 *   3. Module system access (--add-opens, jdeps analysis)
 *   4. GC flag migration (PermGen → Metaspace)
 *   5. New API adoption (var, String methods, Files, HttpClient)
 *   6. Dependency version matrix
 */
public class MigrationGuideExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 15: Java 8 → Java 11 Migration Guide ===\n");

        environmentCheck();
        buildToolMigration();
        gcFlagMigration();
        apiMigrationExamples();
        dependencyVersionMatrix();
        migrationChecklist();
    }

    // -------------------------------------------------------------------------
    // 1. Environment Check — verify current Java version and JVM info
    // -------------------------------------------------------------------------
    static void environmentCheck() {
        System.out.println("--- 1. Environment Check ---");

        System.out.println("  Java version: " + System.getProperty("java.version"));
        System.out.println("  Java vendor:  " + System.getProperty("java.vendor"));
        System.out.println("  Java home:    " + System.getProperty("java.home"));
        System.out.println("  OS:           " + System.getProperty("os.name") + " " + System.getProperty("os.version"));

        int major = Runtime.version().major();
        System.out.println("  Runtime major: " + major);

        if (major < 11) {
            System.out.println("  ⚠ WARNING: Running on Java " + major + " — upgrade to Java 11+");
        } else {
            System.out.println("  ✓ Running on Java " + major + " — migration target met");
        }

        // Runtime.version() — new in Java 9
        Runtime.Version version = Runtime.version();
        System.out.printf("  Full version: %d.%d.%d (build %d)%n",
                version.major(),
                version.minor(),
                version.security(),
                version.build().orElse(0));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Build Tool Migration — Maven / Gradle changes
    // -------------------------------------------------------------------------
    static void buildToolMigration() {
        System.out.println("--- 2. Build Tool Migration ---");

        System.out.println(
            "  Maven — pom.xml changes:\n" +
            "    BEFORE (Java 8):\n" +
            "      <properties>\n" +
            "          <maven.compiler.source>1.8</maven.compiler.source>\n" +
            "          <maven.compiler.target>1.8</maven.compiler.target>\n" +
            "      </properties>\n\n" +
            "    AFTER (Java 11):\n" +
            "      <properties>\n" +
            "          <maven.compiler.source>11</maven.compiler.source>\n" +
            "          <maven.compiler.target>11</maven.compiler.target>\n" +
            "          <!-- Or use release: -->\n" +
            "          <maven.compiler.release>11</maven.compiler.release>\n" +
            "      </properties>\n" +
            "      <!-- Minimum plugin versions for Java 11: -->\n" +
            "      <!-- maven-compiler-plugin: 3.8.0+ -->\n" +
            "      <!-- maven-surefire-plugin: 2.22.0+ -->\n\n" +
            "  Gradle — build.gradle changes:\n" +
            "    BEFORE (Java 8):\n" +
            "      java {\n" +
            "          sourceCompatibility = JavaVersion.VERSION_1_8\n" +
            "          targetCompatibility = JavaVersion.VERSION_1_8\n" +
            "      }\n\n" +
            "    AFTER (Java 11):\n" +
            "      java {\n" +
            "          toolchain { languageVersion = JavaLanguageVersion.of(11) }\n" +
            "      }\n" +
            "      // Or: sourceCompatibility = JavaVersion.VERSION_11\n\n" +
            "  Minimum Gradle version for Java 11: Gradle 5.0+\n" +
            "  Recommended: Gradle 7.0+ (Java toolchains, better module support)\n"
        );
    }

    // -------------------------------------------------------------------------
    // 3. GC Flag Migration — Java 8 flags removed/changed in Java 11
    // -------------------------------------------------------------------------
    static void gcFlagMigration() {
        System.out.println("--- 3. JVM Flag Migration ---");

        System.out.println(
            "  ─────────────────────────────────────────────────────────────────────\n" +
            "  Java 8 Flag             Action in Java 11    Replacement\n" +
            "  ─────────────────────────────────────────────────────────────────────\n" +
            "  -XX:MaxPermSize         REMOVE               -XX:MaxMetaspaceSize\n" +
            "  -XX:PermSize            REMOVE               -XX:MetaspaceSize\n" +
            "  -XX:+PrintGCDetails     REMOVE               -Xlog:gc*\n" +
            "  -XX:+PrintGCDateStamps  REMOVE               -Xlog:gc*:time\n" +
            "  -Xloggc:file.log        REMOVE               -Xlog:gc*:file=gc.log\n" +
            "  -XX:+UseGCLogFileRotation REMOVE             (built into -Xlog)\n" +
            "  -XX:NumberOfGCLogFiles  REMOVE               -Xlog:...:filecount=N\n" +
            "  -XX:GCLogFileSize       REMOVE               -Xlog:...:filesize=Nm\n" +
            "  -XX:+UseConcMarkSweepGC DEPRECATED→REMOVED   Use -XX:+UseG1GC\n" +
            "  -XX:+UseParNewGC        DEPRECATED→REMOVED   Use -XX:+UseG1GC\n" +
            "  -XX:+UnlockCommercialFeatures REMOVE (JFR open-sourced in Java 11)\n" +
            "  -XX:+FlightRecorder     REMOVE (now on by default)\n" +
            "  ─────────────────────────────────────────────────────────────────────\n\n" +
            "  Recommended Java 11 GC flags:\n" +
            "    -Xms512m -Xmx2g\n" +
            "    -XX:+UseG1GC\n" +
            "    -XX:MaxGCPauseMillis=200\n" +
            "    -XX:MetaspaceSize=256m\n" +
            "    -XX:MaxMetaspaceSize=512m\n" +
            "    -Xlog:gc*:file=gc.log:time,level:filecount=5,filesize=20m\n" +
            "    -XX:+HeapDumpOnOutOfMemoryError\n" +
            "    -XX:HeapDumpPath=/var/log/heapdump.hprof\n" +
            "    -XX:StartFlightRecording=settings=default,disk=true,maxage=1h,maxsize=256m\n"
        );

        // Show current Metaspace info
        ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(b -> b.getName().toLowerCase().contains("metaspace"))
                .findFirst()
                .ifPresent(b -> {
                    System.out.printf("  Current Metaspace: used=%d MB  committed=%d MB%n",
                            b.getUsage().getUsed() / (1024 * 1024),
                            b.getUsage().getCommitted() / (1024 * 1024));
                });

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. API Migration — before/after for common patterns
    // -------------------------------------------------------------------------
    static void apiMigrationExamples() {
        System.out.println("--- 4. API Migration Examples ---");

        System.out.println("  Reading a file (Java 8 vs Java 11):");
        System.out.println(
            "    Java 8:\n" +
            "      List<String> lines = new ArrayList<>();\n" +
            "      try (BufferedReader br = new BufferedReader(new FileReader(\"file.txt\"))) {\n" +
            "          String line; while ((line = br.readLine()) != null) lines.add(line);\n" +
            "      }\n\n" +
            "    Java 11:\n" +
            "      String content = Files.readString(Path.of(\"file.txt\"));\n" +
            "      List<String> lines = content.lines().collect(Collectors.toList());\n"
        );

        // String processing — before/after
        System.out.println("  String validation (Java 8 vs Java 11):");
        var inputs = List.of("Alice", "  ", "", "Bob", "\t", "Carol");

        // Java 8 way
        long blankCountJava8 = inputs.stream()
                .filter(s -> s == null || s.trim().isEmpty())  // trim() not Unicode-aware
                .count();

        // Java 11 way
        long blankCountJava11 = inputs.stream()
                .filter(String::isBlank)  // Unicode-aware, cleaner
                .count();

        System.out.println("    Java 8  (trim().isEmpty()): " + blankCountJava8 + " blank");
        System.out.println("    Java 11 (isBlank()):         " + blankCountJava11 + " blank");

        // Collection creation
        System.out.println("\n  Creating immutable collections:");
        System.out.println(
            "    Java 8:  Collections.unmodifiableList(Arrays.asList(\"a\", \"b\", \"c\"))\n" +
            "    Java 9+: List.of(\"a\", \"b\", \"c\")\n"
        );
        var immutable = List.of("a", "b", "c");
        System.out.println("    List.of result: " + immutable);

        // var keyword
        System.out.println("  Type inference with var:");
        System.out.println(
            "    Java 8:  Map<String, List<String>> grouped = new HashMap<>();\n" +
            "    Java 11: var grouped = new HashMap<String, List<String>>();\n"
        );
        var grouped = new HashMap<String, List<String>>();
        grouped.computeIfAbsent("ENG", k -> new ArrayList<>()).add("Alice");
        System.out.println("    var grouped: " + grouped);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Library Version Matrix — minimum versions for Java 11 compatibility
    // -------------------------------------------------------------------------
    static void dependencyVersionMatrix() {
        System.out.println("--- 5. Library Version Matrix for Java 11 ---");

        System.out.println(
            "  Library                Minimum version for Java 11\n" +
            "  ─────────────────────────────────────────────────────────\n" +
            "  Spring Boot            2.1.x  (Java 11 certified)\n" +
            "  Spring Framework       5.1.x\n" +
            "  Hibernate              5.4.x\n" +
            "  Jackson                2.10.x\n" +
            "  Apache Commons Lang    3.9+\n" +
            "  Guava                  27.0+\n" +
            "  Lombok                 1.18.10+\n" +
            "  Log4j2                 2.11.2+\n" +
            "  SLF4J                  1.8.0-beta2+ (or 1.7.x which still works)\n" +
            "  JUnit 5                5.4.0+\n" +
            "  Mockito                2.26.0+\n" +
            "  JAXB (if needed)       jakarta.xml.bind-api 4.0.0\n" +
            "  JAX-WS (if needed)     jakarta.xml.ws-api 4.0.0\n" +
            "  OkHttp                 4.0.0+  (replaces Java's HttpURLConnection)\n" +
            "  Netty                  4.1.50+ \n" +
            "  Vert.x                 4.0.0+\n" +
            "  Quarkus                1.0.0+  (Java 11 native support)\n" +
            "  Micronaut              2.0.0+\n\n" +
            "  Check compatibility: https://wiki.eclipse.org/Eclipse/Java11\n" +
            "                       https://spring.io/blog/2019/03/11/java-11-and-spring-boot-2-1\n"
        );
    }

    // -------------------------------------------------------------------------
    // 6. Complete Migration Checklist
    // -------------------------------------------------------------------------
    static void migrationChecklist() {
        System.out.println("--- 6. Complete Migration Checklist ---");

        System.out.println(
            "  Phase 1: Assessment\n" +
            "    ☐ Run: jdeps --jdk-internals myapp.jar          (find internal API usage)\n" +
            "    ☐ Run: javac --release 11 src/**/*.java         (find compilation errors)\n" +
            "    ☐ Audit all dependencies for Java 11 compatibility\n" +
            "    ☐ Check GC flags in startup scripts / k8s manifests\n\n" +
            "  Phase 2: Dependencies\n" +
            "    ☐ Upgrade build tool (Maven 3.6+, Gradle 5.0+)\n" +
            "    ☐ Set source/target compatibility to 11\n" +
            "    ☐ Add JAXB/JAX-WS dependencies if needed\n" +
            "    ☐ Upgrade all libraries to Java 11-compatible versions\n" +
            "    ☐ Replace javax.* imports with jakarta.* where required\n\n" +
            "  Phase 3: Code Changes\n" +
            "    ☐ Replace sun.misc.BASE64Encoder → java.util.Base64\n" +
            "    ☐ Replace sun.reflect.* → StackWalker / MethodHandles\n" +
            "    ☐ Fix --illegal-access warnings (add --add-opens as needed)\n" +
            "    ☐ Optionally adopt: var, String::isBlank, List.of(), Path.of()\n\n" +
            "  Phase 4: JVM / Runtime\n" +
            "    ☐ Remove -XX:MaxPermSize, -XX:PermSize\n" +
            "    ☐ Add -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m\n" +
            "    ☐ Migrate GC logging to -Xlog:gc*:file=gc.log:time\n" +
            "    ☐ Remove -XX:+UnlockCommercialFeatures (JFR is now free)\n" +
            "    ☐ Switch to -XX:+UseG1GC (or evaluate ZGC)\n" +
            "    ☐ Add -XX:+UseContainerSupport + -XX:MaxRAMPercentage=75 for Docker\n\n" +
            "  Phase 5: Testing\n" +
            "    ☐ Run full test suite on Java 11\n" +
            "    ☐ Check for ClassLoader behavioural changes (especially if multi-layer)\n" +
            "    ☐ Validate application startup time (usually faster in Java 11)\n" +
            "    ☐ Run load test — check GC behaviour, throughput, latency\n" +
            "    ☐ Monitor with JFR during load test\n"
        );
    }
}

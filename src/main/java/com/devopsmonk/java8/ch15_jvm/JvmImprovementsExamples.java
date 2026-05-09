package com.devopsmonk.java8.ch15_jvm;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Chapter 15 — JVM Improvements: Metaspace, Type Annotations, Repeated Annotations
 * Tutorial: https://devops-monk.com/tutorials/java8/jvm-improvements/
 *
 * Covers:
 *  - PermGen removal and Metaspace (conceptual + configuration guidance)
 *  - Type annotations (@Target(ElementType.TYPE_USE)) — JSR 308
 *  - Repeated annotations (@Repeatable) — JSR 175 extension
 *  - Nashorn JavaScript engine (javax.script bridge)
 *  - JVM flags relevant to Java 8 production deployments
 *  - StringJoiner (small but useful new class)
 */
public class JvmImprovementsExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 15: JVM Improvements ===\n");

        metaspaceExplained();
        typeAnnotations();
        repeatedAnnotations();
        parameterNamesViaReflection();
        stringJoiner();
        nashornBridge();
        jvmFlagGuide();
    }

    // -------------------------------------------------------------------------
    // 1. Metaspace — conceptual explanation with measurable indicators
    //
    // PermGen was a fixed-size region of the heap for class metadata.
    // Metaspace lives in native (off-heap) memory and grows automatically.
    // The main observable difference in Java 8:
    //   - -XX:MaxPermSize is IGNORED (prints a warning)
    //   - Use -XX:MaxMetaspaceSize to cap native memory consumption
    //   - OutOfMemoryError: PermGen space → OutOfMemoryError: Metaspace
    // -------------------------------------------------------------------------
    static void metaspaceExplained() {
        System.out.println("--- 1. Metaspace (replaces PermGen) ---");

        // We can observe current Metaspace usage via MemoryPoolMXBean
        java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(b -> b.getName().toLowerCase().contains("metaspace"))
                .forEach(b -> {
                    long usedMB  = b.getUsage().getUsed()      / (1024 * 1024);
                    long commMB  = b.getUsage().getCommitted()  / (1024 * 1024);
                    long maxBytes = b.getUsage().getMax();
                    String maxStr = maxBytes < 0 ? "unlimited" : (maxBytes / (1024 * 1024)) + " MB";
                    System.out.printf("  Pool: %-22s used=%d MB  committed=%d MB  max=%s%n",
                            b.getName(), usedMB, commMB, maxStr);
                });

        // Show that we loaded a lot of classes into Metaspace already
        long loadedClasses = java.lang.management.ManagementFactory
                .getClassLoadingMXBean().getLoadedClassCount();
        System.out.println("  Classes loaded so far: " + loadedClasses);

        // Key points for production
        System.out.println("\n  JVM flag guidance:");
        System.out.println("    REMOVE:  -XX:MaxPermSize (ignored in Java 8, warns on startup)");
        System.out.println("    ADD:     -XX:MetaspaceSize=256m      (initial size, avoids GC churn on startup)");
        System.out.println("    ADD:     -XX:MaxMetaspaceSize=512m   (cap to prevent native OOM on memory leaks)");
        System.out.println("    MONITOR: jcmd <pid> VM.metaspace     (print live Metaspace stats)");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Type Annotations (JSR 308) — @Target(ElementType.TYPE_USE)
    //
    // Before Java 8: annotations could only go on declarations (fields, methods, classes).
    // Java 8 adds TYPE_USE — annotations can now appear almost anywhere a type is used:
    //   @NonNull String, List<@Email String>, new @Validated Widget()
    //
    // The JVM doesn't enforce these — they are consumed by tools like
    // Checker Framework, NullAway, SpotBugs, and IDEs for static analysis.
    // -------------------------------------------------------------------------

    // TYPE_USE annotation — can appear wherever a type name appears
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @interface NonNull {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface Validated {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface Positive {}

    static void typeAnnotations() throws Exception {
        System.out.println("--- 2. Type Annotations (JSR 308) ---");

        // Demonstrating TYPE_USE annotations in various positions
        // (The JVM stores these in class files — reflection can read them)

        // On a field
        @SuppressWarnings("unused")
        class PaymentProcessor {
            @NonNull String currency;           // on a field type
            List<@Validated String> rules;      // inside a generic type argument

            PaymentProcessor(@NonNull String currency) {
                this.currency = currency;
                this.rules = new ArrayList<>();
            }

            // Return type annotation
            @NonNull String getCurrency() { return currency; }

            // Parameter annotation
            void charge(@Positive double amount, @NonNull String description) {
                System.out.printf("  Charging %.2f %s for: %s%n", amount, currency, description);
            }
        }

        PaymentProcessor pp = new PaymentProcessor("GBP");
        pp.charge(99.99, "Annual subscription");

        // Reading annotations from reflection
        Method chargeMethod = PaymentProcessor.class.getDeclaredMethod("charge", double.class, String.class);
        System.out.println("  Method: " + chargeMethod.getName());
        for (Parameter param : chargeMethod.getParameters()) {
            Annotation[] anns = param.getDeclaredAnnotations();
            if (anns.length > 0) {
                System.out.printf("    param %-12s has annotation: %s%n",
                        param.getName(), anns[0].annotationType().getSimpleName());
            }
        }

        // TYPE_USE enables type-checking frameworks to annotate:
        //   @NonNull String[]             -- non-null array of possibly-null strings
        //   @NonNull String @NonNull[]    -- non-null array of non-null strings (two positions!)
        //   Map<@NonNull String, @Positive Integer>
        //   (@NonNull String) rawObject   -- cast
        //   new @Validated Processor()    -- object creation

        System.out.println("  Type annotations are stored in .class files and read by tools like");
        System.out.println("  Checker Framework, NullAway, and IntelliJ IDEA's null analysis.");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Repeated Annotations (@Repeatable) — JSR 175 extension
    //
    // Before Java 8: workaround was a container annotation holding an array.
    //   @Roles({@Role("ADMIN"), @Role("USER")}) -- verbose wrapper
    // Java 8: mark @Role as @Repeatable — compiler generates the container automatically.
    //   @Role("ADMIN") @Role("USER")            -- clean, direct
    // -------------------------------------------------------------------------

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Repeatable(Roles.class)           // points to the container annotation
    @interface Role {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface Roles {                 // container — holds the array
        Role[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Schedules.class)
    @interface Schedule {
        String cron();
        String description() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Schedules { Schedule[] value(); }

    @Role("ADMIN")
    @Role("MANAGER")
    @Role("AUDITOR")
    static class ReportService {

        @Schedule(cron = "0 0 9 * * MON-FRI", description = "Daily morning report")
        @Schedule(cron = "0 0 18 * * FRI",    description = "Weekly summary")
        public void generateReport() {
            System.out.println("  Generating report...");
        }

        @Role("USER")
        @Role("VIEWER")
        public void viewReport() {
            System.out.println("  Viewing report...");
        }
    }

    static void repeatedAnnotations() throws Exception {
        System.out.println("--- 3. Repeated Annotations (@Repeatable) ---");

        // Read class-level repeated annotations
        Role[] classRoles = ReportService.class.getAnnotationsByType(Role.class);
        System.out.println("  ReportService allowed roles:");
        for (Role r : classRoles) System.out.println("    @Role(\"" + r.value() + "\")");

        // Read method-level repeated annotations
        Method generate = ReportService.class.getDeclaredMethod("generateReport");
        Schedule[] schedules = generate.getAnnotationsByType(Schedule.class);
        System.out.println("\n  generateReport() schedules:");
        for (Schedule s : schedules)
            System.out.printf("    cron=\"%-25s\"  desc=\"%s\"%n", s.cron(), s.description());

        Method view = ReportService.class.getDeclaredMethod("viewReport");
        Role[] viewRoles = view.getAnnotationsByType(Role.class);
        System.out.println("\n  viewReport() allowed roles:");
        for (Role r : viewRoles) System.out.println("    @Role(\"" + r.value() + "\")");

        // getAnnotationsByType vs getDeclaredAnnotationsByType
        // getAnnotationsByType also looks at inherited annotations
        // getDeclaredAnnotationsByType only looks at the element itself

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Parameter Names via Reflection — requires -parameters compiler flag
    //
    // Before Java 8: reflection couldn't retrieve parameter names (arg0, arg1...)
    // Java 8: compile with javac -parameters to preserve names in class files.
    //   In Gradle: compileJava.options.compilerArgs = ['-parameters']
    //   Then Parameter.getName() returns the real name instead of "arg0".
    // -------------------------------------------------------------------------
    static void parameterNamesViaReflection() throws Exception {
        System.out.println("--- 4. Parameter Names via Reflection ---");

        class TransferService {
            public void transfer(String fromAccount, String toAccount, double amount) { }
        }

        Method transfer = TransferService.class.getDeclaredMethod(
                "transfer", String.class, String.class, double.class);

        System.out.println("  Method: " + transfer.getName());
        for (Parameter param : transfer.getParameters()) {
            System.out.printf("    type=%-10s name=%-15s isNamePresent=%b%n",
                    param.getType().getSimpleName(),
                    param.getName(),
                    param.isNamePresent());
        }

        System.out.println("  Note: To get real names (isNamePresent=true), compile with:");
        System.out.println("    javac -parameters  (Gradle: options.compilerArgs += ['-parameters'])");
        System.out.println("  Frameworks like Spring use this to avoid @RequestParam(\"name\") boilerplate.");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. StringJoiner — cleaner delimiter-joined strings
    //
    // New in Java 8. Wraps StringBuilder for building "a, b, c" style strings.
    // String.join() is a convenience wrapper around StringJoiner.
    // -------------------------------------------------------------------------
    static void stringJoiner() {
        System.out.println("--- 5. StringJoiner ---");

        // Basic use
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add("Alice").add("Bob").add("Carol");
        System.out.println("Basic join:      " + joiner);

        // With prefix and suffix
        StringJoiner withBrackets = new StringJoiner(", ", "[", "]");
        withBrackets.add("Java").add("Streams").add("Lambdas");
        System.out.println("With brackets:   " + withBrackets);

        // Empty StringJoiner with setEmptyValue
        StringJoiner empty = new StringJoiner(", ", "{", "}");
        empty.setEmptyValue("{}");  // returned instead of "{}" when nothing added
        System.out.println("Empty value:     " + empty);

        // Merge two StringJoiners
        StringJoiner first  = new StringJoiner(", ").add("one").add("two");
        StringJoiner second = new StringJoiner(", ").add("three").add("four");
        first.merge(second);
        System.out.println("After merge:     " + first);

        // String.join shorthand — most common case
        String csv = String.join(", ", "one", "two", "three");
        System.out.println("String.join:     " + csv);

        // String.join with a List
        List<String> tags = Arrays.asList("#java", "#java8", "#streams");
        String tagLine = String.join(" ", tags);
        System.out.println("Tags:            " + tagLine);

        // Collectors.joining in streams — backed by StringJoiner under the hood
        String report = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> "Item-" + i)
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        System.out.println("Collectors.joining: " + report);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Nashorn JavaScript Engine
    //
    // Java 8 ships Nashorn, a new high-performance JS engine replacing Rhino.
    // Useful for: config scripts, user-supplied expression evaluation,
    //             server-side template rendering, scripting CLI tools.
    // Note: Nashorn is deprecated in Java 11 and removed in Java 15.
    //       For Java 11+ use GraalVM's Polyglot API instead.
    // -------------------------------------------------------------------------
    static void nashornBridge() {
        System.out.println("--- 6. Nashorn JavaScript Engine ---");

        try {
            javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = manager.getEngineByName("nashorn");

            if (engine == null) {
                System.out.println("  Nashorn not available on this JVM (Java 15+).");
                System.out.println("  Use GraalVM Polyglot API for JavaScript in Java 11+.");
                System.out.println();
                return;
            }

            // Evaluate a simple expression
            Object result = engine.eval("3 * 7 + Math.PI");
            System.out.println("  JS eval: 3 * 7 + Math.PI = " + result);

            // Call a JS function from Java — useful for config expressions
            engine.eval(
                "function discount(price, pct) { return price * (1 - pct / 100); }"
            );
            javax.script.Invocable invocable = (javax.script.Invocable) engine;
            double discounted = (double) invocable.invokeFunction("discount", 499.99, 20);
            System.out.printf("  JS function discount(499.99, 20) = £%.2f%n", discounted);

            // Pass Java objects to JS
            engine.put("today", java.time.LocalDate.now().toString());
            Object greeting = engine.eval("'Hello from Nashorn on ' + today");
            System.out.println("  " + greeting);

            // JSON parse — Nashorn has built-in JSON
            engine.eval(
                "var config = JSON.parse('{\"timeout\": 30, \"retries\": 3}');"
            );
            Object timeout = engine.eval("config.timeout");
            System.out.println("  JSON config.timeout = " + timeout);

        } catch (Exception e) {
            System.out.println("  Nashorn error: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. JVM Flag Quick Reference for Java 8 Production Deployments
    // -------------------------------------------------------------------------
    static void jvmFlagGuide() {
        System.out.println("--- 7. Java 8 JVM Flag Quick Reference ---");

        System.out.println(
            "  MEMORY\n" +
            "    -Xms512m -Xmx2g                — heap min/max (set equal to avoid resizing pauses)\n" +
            "    -XX:MetaspaceSize=256m          — initial Metaspace (avoids GC churn on startup)\n" +
            "    -XX:MaxMetaspaceSize=512m       — cap native memory for class metadata\n" +
            "    -XX:+UseStringDeduplication     — (G1 only) dedup identical String objects\n" +
            "\n" +
            "  GARBAGE COLLECTION\n" +
            "    -XX:+UseG1GC                    — G1 is default in Java 9+, opt in for Java 8\n" +
            "    -XX:MaxGCPauseMillis=200        — G1 pause-time target (best-effort)\n" +
            "    -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log  — GC logging\n" +
            "    -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=20m\n" +
            "\n" +
            "  THREAD & CONCURRENCY\n" +
            "    -Djava.util.concurrent.ForkJoinPool.common.parallelism=4  — parallel stream threads\n" +
            "    -Xss256k                        — stack size per thread (reduce for many threads)\n" +
            "\n" +
            "  DIAGNOSTICS\n" +
            "    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/app.hprof\n" +
            "    -XX:+PrintCompilation           — JIT compilation events\n" +
            "    -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining  — method inlining events\n" +
            "\n" +
            "  REMOVE IN JAVA 8\n" +
            "    -XX:MaxPermSize    → REMOVE (ignored, prints warning)\n" +
            "    -XX:PermSize       → REMOVE (same)\n"
        );

        // Print the current JVM's key flags that are actually set
        System.out.println("  Current JVM memory settings:");
        Runtime rt = Runtime.getRuntime();
        long maxMB  = rt.maxMemory()   / (1024 * 1024);
        long totMB  = rt.totalMemory() / (1024 * 1024);
        long freeMB = rt.freeMemory()  / (1024 * 1024);
        System.out.printf("    max=%-5d MB  total=%-5d MB  free=%-5d MB  used=%-5d MB%n",
                maxMB, totMB, freeMB, totMB - freeMB);
        System.out.println("    Available processors: " + rt.availableProcessors());

        System.out.println();
    }
}

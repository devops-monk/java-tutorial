package com.devopsmonk.java11.ch10_tooling;

import javax.script.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;

/**
 * Chapter 10 — Tooling: JShell, jlink, and Single-File Programs (JEP 222, 282, 330)
 * Tutorial: https://devops-monk.com/tutorials/java11/tooling-jshell-jlink/
 *
 * JShell (Java 9) — interactive REPL for Java
 *   Launch: jshell
 *   API:    javax.script or jdk.jshell for programmatic use
 *
 * Single-file programs (Java 11, JEP 330):
 *   java HelloWorld.java  — compiles and runs in one step (no javac, no class file)
 *   #!/usr/bin/java --source 11  — shebang support on Unix
 *
 * jlink (Java 9, JEP 282):
 *   jlink --module-path $JAVA_HOME/jmods \
 *         --add-modules java.base,java.net.http \
 *         --output custom-jre
 *   Creates a minimal JRE image containing only the modules your app needs.
 */
public class ToolingExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 10: Tooling — JShell, jlink, Single-File Programs ===\n");

        jshellConcepts();
        jshellApiDemo();
        singleFileProgramsExamples();
        jlinkGuide();
        jdepsDemo();
        developerWorkflowTips();
    }

    // -------------------------------------------------------------------------
    // 1. JShell Concepts — what it is and common commands
    // -------------------------------------------------------------------------
    static void jshellConcepts() {
        System.out.println("--- 1. JShell: The Java REPL ---");

        System.out.println(
            "  Launch JShell:  $ jshell\n\n" +
            "  Common commands inside JShell:\n" +
            "    /list           — show all snippets entered so far\n" +
            "    /vars           — show all declared variables\n" +
            "    /methods        — show all declared methods\n" +
            "    /types          — show all declared types\n" +
            "    /edit <id>      — open snippet in editor\n" +
            "    /drop <id>      — remove a snippet\n" +
            "    /reset          — clear all state\n" +
            "    /save file.jsh  — save session to file\n" +
            "    /open file.jsh  — load and run a saved file\n" +
            "    /exit           — quit JShell\n\n" +
            "  JShell features:\n" +
            "    ✓ No class or main() needed — type any expression\n" +
            "    ✓ Implicit imports: java.util.*, java.io.*, java.math.*, etc.\n" +
            "    ✓ Tab completion on types, methods, and imports\n" +
            "    ✓ Auto-prints the result of expressions\n" +
            "    ✓ /!  — re-run last snippet\n" +
            "    ✓ /<id> — re-run snippet by ID\n\n" +
            "  Example session:\n" +
            "    jshell> var list = List.of(1, 2, 3, 4, 5)\n" +
            "    list ==> [1, 2, 3, 4, 5]\n" +
            "    jshell> list.stream().filter(n -> n % 2 == 0).toList()\n" +
            "    $2 ==> [2, 4]\n" +
            "    jshell> int factorial(int n) { return n <= 1 ? 1 : n * factorial(n-1); }\n" +
            "    |  created method factorial(int)\n" +
            "    jshell> factorial(6)\n" +
            "    $4 ==> 720\n"
        );
    }

    // -------------------------------------------------------------------------
    // 2. JShell API — run Java snippets programmatically (for testing/teaching)
    // -------------------------------------------------------------------------
    static void jshellApiDemo() throws Exception {
        System.out.println("--- 2. Scripting Engine (javax.script) ---");

        // Note: JShell's public API is in jdk.jshell (not shipped in all JREs).
        // We demo the scripting bridge which is available in all Java 11 JDKs.

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("javascript");  // Nashorn in Java 11

        if (engine == null) {
            System.out.println("  JavaScript engine not available (Java 15+ removed Nashorn).");
            System.out.println("  For Java 11 REPL evaluation, use jdk.jshell directly.");
        } else {
            // Evaluate arithmetic
            Object result1 = engine.eval("3 * 7 + Math.PI");
            System.out.printf("  JS: 3 * 7 + Math.PI = %.4f%n", ((Number)result1).doubleValue());

            // Call a function defined in JS from Java
            engine.eval("function greet(name) { return 'Hello, ' + name + '!'; }");
            Invocable inv = (Invocable) engine;
            Object greeting = inv.invokeFunction("greet", "Java 11");
            System.out.println("  JS function: " + greeting);

            // Pass Java objects into the script context
            engine.put("employees", java.util.List.of("Alice", "Bob", "Carol"));
            Object count = engine.eval("employees.size()");
            System.out.println("  Java list in JS, size(): " + count);
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Single-File Programs (JEP 330) — run .java files directly
    // -------------------------------------------------------------------------
    static void singleFileProgramsExamples() throws Exception {
        System.out.println("--- 3. Single-File Programs (JEP 330) ---");

        System.out.println(
            "  java HelloWorld.java          — compile + run in one command\n" +
            "  java --source 11 script.java  — specify source version explicitly\n\n" +
            "  Use cases:\n" +
            "    ✓ Quick scripts and CLI tools\n" +
            "    ✓ Teaching — no build system needed\n" +
            "    ✓ Shebang scripts on Unix:\n" +
            "        #!/usr/bin/java --source 11\n" +
            "        public class Script {\n" +
            "            public static void main(String[] args) {\n" +
            "                System.out.println(\"Hello from a Java script!\");\n" +
            "            }\n" +
            "        }\n\n" +
            "  Limitations:\n" +
            "    ✗ Only ONE source file — multi-file projects need javac\n" +
            "    ✗ No IDE support for shebang execution\n" +
            "    ✗ Dependencies must be on the classpath (--class-path flag)\n"
        );

        // Demo: write a single-file Java program to disk and run it
        Path tempScript = Files.createTempFile("Hello", ".java");
        Files.writeString(tempScript, """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello from a single-file Java program!");
                        System.out.println("Java version: " + System.getProperty("java.version"));
                    }
                }
                """);

        System.out.println("  Running single-file program: " + tempScript.getFileName());
        ProcessBuilder pb = new ProcessBuilder("java", tempScript.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(l -> System.out.println("  > " + l));
        }
        process.waitFor();
        Files.deleteIfExists(tempScript);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. jlink — build a minimal custom JRE
    // -------------------------------------------------------------------------
    static void jlinkGuide() {
        System.out.println("--- 4. jlink: Build a Minimal JRE ---");

        System.out.println(
            "  # Find which modules your app needs:\n" +
            "  jdeps --module-path app.jar --print-module-deps app.jar\n\n" +
            "  # Build a minimal JRE with only those modules:\n" +
            "  jlink \\\n" +
            "    --module-path $JAVA_HOME/jmods \\\n" +
            "    --add-modules java.base,java.net.http,java.logging \\\n" +
            "    --strip-debug \\\n" +
            "    --no-header-files \\\n" +
            "    --no-man-pages \\\n" +
            "    --compress=2 \\\n" +
            "    --output custom-jre\n\n" +
            "  # Size comparison (approximate):\n" +
            "    Full JDK 11:         ~300 MB\n" +
            "    jlink (java.base):   ~38 MB\n" +
            "    jlink + net.http:    ~45 MB\n\n" +
            "  # Run with the custom JRE:\n" +
            "  custom-jre/bin/java -jar app.jar\n\n" +
            "  # Docker example:\n" +
            "  FROM eclipse-temurin:11 AS build\n" +
            "  COPY . .\n" +
            "  RUN jlink --add-modules $(jdeps --print-module-deps app.jar) \\\n" +
            "       --output /custom-jre\n\n" +
            "  FROM debian:slim\n" +
            "  COPY --from=build /custom-jre /opt/java\n" +
            "  ENTRYPOINT [\"/opt/java/bin/java\", \"-jar\", \"app.jar\"]\n"
        );

        // Show modules available in this JRE
        System.out.println("  Modules in this JRE's boot layer:");
        ModuleLayer.boot().modules().stream()
                .map(Module::getName)
                .filter(n -> n.startsWith("java."))
                .sorted()
                .forEach(n -> System.out.println("    " + n));
    }

    // -------------------------------------------------------------------------
    // 5. jdeps — analyse dependencies
    // -------------------------------------------------------------------------
    static void jdepsDemo() {
        System.out.println("\n--- 5. jdeps: Dependency Analysis ---");

        System.out.println(
            "  # Find all JDK internal API usages (migration target):\n" +
            "  jdeps --jdk-internals myapp.jar\n\n" +
            "  # Find required modules (input for jlink):\n" +
            "  jdeps --print-module-deps myapp.jar\n\n" +
            "  # Full dependency report:\n" +
            "  jdeps -verbose:class myapp.jar\n\n" +
            "  # Check for split packages:\n" +
            "  jdeps --check com.example.mymodule myapp.jar\n\n" +
            "  # Typical output:\n" +
            "  myapp.jar -> java.base\n" +
            "  myapp.jar -> java.net.http\n" +
            "  myapp.jar -> java.logging\n" +
            "  myapp.jar -> JDK internal API: sun.misc.BASE64Encoder (replace with java.util.Base64)\n"
        );

        // Show JVM process info available via ManagementFactory
        System.out.println("  Current JVM info:");
        var runtimeMX = ManagementFactory.getRuntimeMXBean();
        System.out.println("    VM name:    " + runtimeMX.getVmName());
        System.out.println("    VM vendor:  " + runtimeMX.getVmVendor());
        System.out.println("    VM version: " + runtimeMX.getVmVersion());
        System.out.println("    Uptime:     " + runtimeMX.getUptime() + "ms");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Developer Workflow Tips
    // -------------------------------------------------------------------------
    static void developerWorkflowTips() {
        System.out.println("--- 6. Developer Workflow Tips ---");

        System.out.println(
            "  JShell use cases:\n" +
            "    ✓ Test a regex without writing a test class\n" +
            "    ✓ Explore an unfamiliar API interactively\n" +
            "    ✓ Verify date/time formatting or edge cases\n" +
            "    ✓ Quick prototyping before writing real code\n\n" +
            "  Useful JShell startup tricks:\n" +
            "    jshell --startup DEFAULT --startup mySetup.jsh\n" +
            "    # mySetup.jsh can import your domain classes:\n" +
            "    /env -class-path target/classes\n" +
            "    import com.mycompany.model.*;\n\n" +
            "  Single-file program for DevOps scripts:\n" +
            "    #!/usr/bin/java --source 11\n" +
            "    // parse_log.java — run with: ./parse_log.java access.log\n" +
            "    import java.nio.file.*; import java.util.stream.*;\n" +
            "    class ParseLog {\n" +
            "        public static void main(String[] args) throws Exception {\n" +
            "            Files.lines(Path.of(args[0]))\n" +
            "                 .filter(l -> l.contains(\"ERROR\"))\n" +
            "                 .forEach(System.out::println);\n" +
            "        }\n" +
            "    }\n"
        );
    }
}

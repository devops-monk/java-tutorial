package com.devopsmonk.java11.ch14_removed;

import jakarta.xml.bind.*;
import javax.xml.parsers.*;
import java.io.StringReader;
import java.util.*;
import java.util.logging.*;
import org.xml.sax.InputSource;

/**
 * Chapter 14 — Removed and Deprecated APIs: Java EE Modules, JavaFX, Nashorn
 * Tutorial: https://devops-monk.com/tutorials/java11/removed-deprecated-apis/
 *
 * Java 9–11 removed APIs that were previously bundled in the JDK:
 *
 *   Removed in Java 11 (JEP 320 — Remove Java EE and CORBA modules):
 *     java.xml.bind  (JAXB)         → jakarta.xml.bind:jakarta.xml.bind-api
 *     java.xml.ws    (JAX-WS)       → jakarta.xml.ws:jakarta.xml.ws-api
 *     java.xml.ws.annotation        → jakarta.annotation:jakarta.annotation-api
 *     java.activation               → jakarta.activation:jakarta.activation-api
 *     java.corba     (CORBA/RMI-IIOP) → no viable replacement; use gRPC instead
 *     java.transaction              → jakarta.transaction:jakarta.transaction-api
 *
 *   Deprecated in Java 11:
 *     Nashorn JS engine (removed in Java 15)  → GraalVM Polyglot
 *     Pack200 (removed in Java 14)
 *
 *   Removed in Java 11 (JEP 335, 336):
 *     JavaFX (moved to OpenJFX, separate download)
 *
 *   Removed in Java 9 (internal APIs):
 *     sun.misc.BASE64Encoder/Decoder  → java.util.Base64
 *     sun.reflect.*                   → java.lang.reflect + MethodHandles
 *     com.sun.image.*                 → use ImageIO
 *
 * BUILD.GRADLE for these examples needs:
 *   implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.0'
 *   runtimeOnly    'com.sun.xml.bind:jaxb-impl:4.0.5'
 */
public class RemovedApisExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 14: Removed and Deprecated APIs ===\n");

        jaxbMigration();
        base64Migration();
        nashornMigration();
        javafxGuidance();
        internalApiMigration();
        migrationChecklist();
    }

    // -------------------------------------------------------------------------
    // 1. JAXB Migration — xml.bind removed in Java 11
    // -------------------------------------------------------------------------
    @XmlRootElement(name = "employee")
    @XmlAccessorType(XmlAccessType.FIELD)
    static class Employee {
        @XmlAttribute long id;
        @XmlElement  String name;
        @XmlElement  String department;
        @XmlElement  double salary;

        Employee() {}
        Employee(long id, String name, String department, double salary) {
            this.id = id; this.name = name;
            this.department = department; this.salary = salary;
        }
        @Override public String toString() {
            return String.format("Employee{id=%d, name='%s', dept='%s', salary=%.0f}",
                    id, name, department, salary);
        }
    }

    static void jaxbMigration() throws Exception {
        System.out.println("--- 1. JAXB Migration ---");

        System.out.println(
            "  Before Java 11: JAXB was bundled in java.xml.bind module.\n" +
            "  Java 11+: add to build.gradle:\n" +
            "    implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.0'\n" +
            "    runtimeOnly    'com.sun.xml.bind:jaxb-impl:4.0.5'\n\n" +
            "  Package rename: javax.xml.bind.* → jakarta.xml.bind.*\n"
        );

        try {
            // Marshal (Java object → XML)
            var employee = new Employee(1, "Alice", "Engineering", 95000);
            JAXBContext ctx = JAXBContext.newInstance(Employee.class);

            java.io.StringWriter sw = new java.io.StringWriter();
            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(employee, sw);
            String xml = sw.toString();

            System.out.println("  Marshalled XML:");
            xml.lines().forEach(l -> System.out.println("    " + l));

            // Unmarshal (XML → Java object)
            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            Employee restored = (Employee) unmarshaller.unmarshal(new StringReader(xml));
            System.out.println("  Unmarshalled: " + restored);

        } catch (ClassNotFoundException | JAXBException e) {
            System.out.println("  [JAXB not on classpath — add dependency to build.gradle]");
            System.out.println("  When present: marshal/unmarshal works identically to Java 8");
            System.out.println("  Only difference: change import javax.xml.bind.* → jakarta.xml.bind.*");
        } catch (Exception e) {
            System.out.println("  JAXB demo: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Base64 Migration — sun.misc.BASE64Encoder removed in Java 9
    // -------------------------------------------------------------------------
    static void base64Migration() {
        System.out.println("--- 2. Base64 Migration ---");

        System.out.println(
            "  Before Java 8: sun.misc.BASE64Encoder (internal, non-public)\n" +
            "  Java 8+: java.util.Base64 (public API, available since Java 8)\n"
        );

        // java.util.Base64 — the correct API since Java 8
        String credentials = "alice:s3cr3t-p@ssword";
        String encoded     = Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader  = "Basic " + encoded;

        System.out.println("  Credentials:   " + credentials);
        System.out.println("  Base64 encoded: " + encoded);
        System.out.println("  Auth header:    " + authHeader);

        // Decode
        String raw   = authHeader.substring("Basic ".length());
        String[] kv  = new String(Base64.getDecoder().decode(raw)).split(":", 2);
        System.out.println("  Decoded user:   " + kv[0]);
        System.out.println("  Decoded pass:   " + kv[1]);

        // URL-safe Base64 (for JWT tokens, OAuth state, etc.)
        String urlSafe = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Java 11: /path?key=val&other=123".getBytes());
        System.out.println("  URL-safe (no padding): " + urlSafe);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Nashorn Deprecation/Removal — deprecated Java 11, removed Java 15
    // -------------------------------------------------------------------------
    static void nashornMigration() {
        System.out.println("--- 3. Nashorn → GraalVM Polyglot Migration ---");

        System.out.println(
            "  Nashorn:  deprecated Java 11, removed Java 15\n" +
            "  GraalVM Polyglot API: the modern replacement\n\n" +
            "  Java 11 Nashorn usage (still works, warns about deprecation):\n" +
            "    ScriptEngine engine = new ScriptEngineManager().getEngineByName(\"nashorn\");\n" +
            "    engine.eval(\"print('hello')\");\n\n" +
            "  GraalVM Polyglot (add org.graalvm.sdk:graal-sdk:21.3.0):\n" +
            "    try (Context context = Context.create()) {\n" +
            "        Value result = context.eval(\"js\", \"3 * 7 + Math.PI\");\n" +
            "        System.out.println(result.asDouble());\n" +
            "    }\n\n" +
            "  Alternative for expression evaluation (no JS engine needed):\n" +
            "    Use scripting DSLs (MVEL, OGNL) or Spring Expression Language (SpEL)\n" +
            "    for dynamic expression evaluation without embedding a full JS engine.\n"
        );

        // Try Nashorn if available (works on Java 11, fails on 15+)
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager()
                    .getEngineByName("javascript");
            if (engine != null) {
                Object result = engine.eval("3 * 7 + Math.PI");
                System.out.println("  Nashorn still available, 3*7+PI = " + result);
            } else {
                System.out.println("  Nashorn removed in this JVM — migrate to GraalVM Polyglot");
            }
        } catch (Exception e) {
            System.out.println("  Nashorn unavailable: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. JavaFX Guidance
    // -------------------------------------------------------------------------
    static void javafxGuidance() {
        System.out.println("--- 4. JavaFX → OpenJFX ---");

        System.out.println(
            "  JavaFX was bundled in Oracle JDK 8, removed from JDK 11.\n" +
            "  Now maintained as OpenJFX — download separately.\n\n" +
            "  Add to build.gradle:\n" +
            "    plugins { id 'org.openjfx.javafxplugin' version '0.1.0' }\n" +
            "    javafx { version = '21'; modules = ['javafx.controls', 'javafx.fxml'] }\n\n" +
            "  Or as dependency:\n" +
            "    implementation 'org.openjfx:javafx-controls:21'\n" +
            "    implementation 'org.openjfx:javafx-fxml:21'\n\n" +
            "  OpenJFX download: https://openjfx.io\n\n" +
            "  Alternatives to JavaFX for desktop GUIs:\n" +
            "    Swing (still bundled, no removal plans)\n" +
            "    SWT (Eclipse's toolkit — used by IntelliJ IDEA)\n" +
            "    JavaFX via OpenJFX (recommended if you were on JavaFX 8)\n"
        );
    }

    // -------------------------------------------------------------------------
    // 5. Internal API Migration
    // -------------------------------------------------------------------------
    static void internalApiMigration() {
        System.out.println("--- 5. Internal API Migration Guide ---");

        System.out.println(
            "  ─────────────────────────────────────────────────────────────────────\n" +
            "  Internal API                 → Java 11 Public Replacement\n" +
            "  ─────────────────────────────────────────────────────────────────────\n" +
            "  sun.misc.BASE64Encoder       → java.util.Base64 (available since Java 8)\n" +
            "  sun.misc.Unsafe.allocateMemory → ByteBuffer.allocateDirect() for most use\n" +
            "  sun.reflect.Reflection       → StackWalker (Java 9, JEP 259)\n" +
            "  com.sun.net.httpserver       → still available in Java 11 (not removed)\n" +
            "  javax.xml.bind.*             → jakarta.xml.bind.* (add dependency)\n" +
            "  javax.xml.ws.*               → jakarta.xml.ws.*   (add dependency)\n" +
            "  javax.activation.*           → jakarta.activation.* (add dependency)\n" +
            "  com.sun.image.codec.jpeg.*   → javax.imageio.ImageIO\n" +
            "  sun.awt.*                    → java.awt (where possible)\n" +
            "  ─────────────────────────────────────────────────────────────────────\n\n" +
            "  Find violations before migration:\n" +
            "    jdeps --jdk-internals myapp.jar --multi-release 11\n\n" +
            "  Emergency escape hatch (do not use long-term):\n" +
            "    --add-exports java.base/sun.misc=ALL-UNNAMED\n"
        );

        // Demonstrate StackWalker (replaces sun.reflect.Reflection.getCallerClass())
        System.out.println("  StackWalker (replaces sun.reflect.Reflection.getCallerClass()):");
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        walker.walk(frames -> frames
                .limit(3)
                .map(f -> "    " + f.getClassName() + "." + f.getMethodName()
                        + ":" + f.getLineNumber())
                .forEach(System.out::println)
        );

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Migration Checklist
    // -------------------------------------------------------------------------
    static void migrationChecklist() {
        System.out.println("--- 6. Java 8 → 11 Removal Checklist ---");

        System.out.println(
            "  Step 1: Find internal API usage\n" +
            "    jdeps --jdk-internals --multi-release 11 myapp.jar libs/*.jar\n\n" +
            "  Step 2: Find removed module usage\n" +
            "    javac --release 11 src/**/*.java  (compile errors = removed APIs)\n\n" +
            "  Step 3: Add Jakarta EE replacements (if needed)\n" +
            "    implementation 'jakarta.xml.bind:jakarta.xml.bind-api:4.0.0'\n" +
            "    runtimeOnly    'com.sun.xml.bind:jaxb-impl:4.0.5'\n" +
            "    implementation 'jakarta.xml.ws:jakarta.xml.ws-api:4.0.0'\n" +
            "    implementation 'jakarta.annotation:jakarta.annotation-api:2.1.1'\n\n" +
            "  Step 4: Update import statements\n" +
            "    javax.xml.bind.* → jakarta.xml.bind.*\n" +
            "    javax.xml.ws.*   → jakarta.xml.ws.*\n" +
            "    (Use IDE global find+replace or sed)\n\n" +
            "  Step 5: Run with --illegal-access=warn (Java 11 default) first\n" +
            "    java --illegal-access=warn -jar app.jar\n" +
            "    Fix all warnings before Java 17 (--illegal-access removed)\n\n" +
            "  Step 6: Test with GraalVM native-image if targeting native compilation\n"
        );
    }
}

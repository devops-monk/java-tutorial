package com.devopsmonk.java11.ch16_bestpractices;

import java.lang.management.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.*;

/**
 * Chapter 16 — Java 11 Production Checklist and Best Practices
 * Tutorial: https://devops-monk.com/tutorials/java11/production-best-practices/
 *
 * Covers the patterns, idioms, and configuration that separate a Java 11 app
 * that merely compiles on Java 11 from one that RUNS WELL on Java 11.
 *
 * Sections:
 *   1. Idiomatic Java 11 code patterns
 *   2. HttpClient best practices
 *   3. Immutability with factory collections
 *   4. String handling improvements
 *   5. Production JVM configuration reference
 *   6. Performance benchmarks: Java 8 vs Java 11
 *   7. Security hardening checklist
 */
public class Java11BestPracticesExamples {

    // One shared HttpClient — manages connection pool and HTTP/2 sessions
    static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 16: Java 11 Best Practices ===\n");

        idiomaticJava11();
        httpClientBestPractices();
        immutableCollectionPatterns();
        stringHandling();
        varBestPractices();
        productionJvmReference();
        performanceComparison();
        securityChecklist();
    }

    // -------------------------------------------------------------------------
    // 1. Idiomatic Java 11 Patterns
    // -------------------------------------------------------------------------
    static void idiomaticJava11() {
        System.out.println("--- 1. Idiomatic Java 11 Patterns ---");

        // ✓ Use var for obvious types, explicit types when it adds clarity
        var employees = List.of("Alice:ENG:95000", "Bob:PROD:82000", "Carol:ENG:91000");
        var byDept = employees.stream()
                .map(s -> s.split(":"))
                .collect(Collectors.groupingBy(p -> p[1],
                         Collectors.mapping(p -> p[0], Collectors.toList())));
        System.out.println("  By dept: " + byDept);

        // ✓ isBlank() instead of trim().isEmpty()
        var rawInput = "   ";
        if (rawInput.isBlank()) {
            System.out.println("  Input is blank (Unicode-aware)");
        }

        // ✓ lines() + Predicate.not for config/log parsing
        String config = "host=localhost\n# comment\nport=5432\n\ntimeout=30";
        var settings = config.lines()
                .filter(Predicate.not(String::isBlank))
                .filter(Predicate.not(l -> l.startsWith("#")))
                .map(l -> l.split("=", 2))
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
        System.out.println("  Parsed config: " + settings);

        // ✓ Optional.ifPresentOrElse — no isPresent() + get() pattern
        findEmployee("alice").ifPresentOrElse(
                emp -> System.out.println("  Found: " + emp),
                ()  -> System.out.println("  Not found — using default")
        );

        // ✓ Stream.ofNullable — flatten nullable into stream pipelines
        List<String> possiblyNullNames = Arrays.asList("Alice", null, "Bob", null, "Carol");
        var nonNullNames = possiblyNullNames.stream()
                .flatMap(Stream::ofNullable)
                .collect(Collectors.toList());
        System.out.println("  Non-null names: " + nonNullNames);

        // ✓ Collectors.toUnmodifiableList — return immutable from service methods
        List<String> safeResult = employees.stream()
                .filter(e -> e.contains("ENG"))
                .collect(Collectors.toUnmodifiableList());
        System.out.println("  Unmodifiable ENG: " + safeResult);

        System.out.println();
    }

    static Optional<String> findEmployee(String name) {
        var db = Map.of("alice", "Alice (ENG, £95k)", "bob", "Bob (PROD, £82k)");
        return Optional.ofNullable(db.get(name.toLowerCase()));
    }

    // -------------------------------------------------------------------------
    // 2. HttpClient Best Practices
    // -------------------------------------------------------------------------
    static void httpClientBestPractices() {
        System.out.println("--- 2. HttpClient Best Practices ---");

        System.out.println(
            "  ✓ Create ONE HttpClient per application — it's a connection pool\n" +
            "  ✓ Reuse HttpRequest objects for the same endpoint (they're immutable)\n" +
            "  ✓ Always set a timeout — never block indefinitely\n" +
            "  ✓ Use sendAsync() for concurrent I/O — don't loop sync calls\n" +
            "  ✓ Handle exceptionally() — async failures are silent without it\n" +
            "  ✓ Prefer BodyHandlers.ofString() for JSON; use BodyHandlers.ofInputStream() for streams\n\n" +
            "  ✗ Don't create a new HttpClient per request (wastes TCP connections)\n" +
            "  ✗ Don't call .get() without a timeout on async futures\n" +
            "  ✗ Don't ignore HttpTimeoutException — add retry or circuit-breaker logic\n"
        );

        // Show: reuse a request template, change just the URI
        var baseTemplate = HttpRequest.newBuilder()
                .header("Accept", "application/json")
                .header("User-Agent", "java11-tutorial/1.0")
                .timeout(Duration.ofSeconds(5));

        // Demonstrate async fire-and-forget pattern (handles failures gracefully)
        var endpoints = List.of("/users", "/products", "/orders");
        System.out.println("  Would fire " + endpoints.size() + " async requests concurrently:");
        endpoints.forEach(ep -> System.out.println("    GET https://api.example.com" + ep));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Immutable Collection Patterns
    // -------------------------------------------------------------------------
    static void immutableCollectionPatterns() {
        System.out.println("--- 3. Immutable Collection Patterns ---");

        // Pattern 1: Constant lookup tables — define as static final, use List/Map/Set.of
        final var VALID_DEPARTMENTS = Set.of("ENGINEERING", "PRODUCT", "DESIGN", "MARKETING");
        final var HTTP_STATUS_MESSAGES = Map.of(
                200, "OK", 201, "Created", 400, "Bad Request",
                401, "Unauthorized", 403, "Forbidden", 404, "Not Found", 500, "Internal Server Error"
        );

        System.out.println("  Valid departments: " + VALID_DEPARTMENTS);
        System.out.println("  HTTP 404: " + HTTP_STATUS_MESSAGES.get(404));

        // Pattern 2: Validate input against allowed set
        String inputDept = "SALES";
        if (!VALID_DEPARTMENTS.contains(inputDept)) {
            System.out.println("  Invalid department: " + inputDept);
        }

        // Pattern 3: Build mutable, then freeze
        var mutableConfig = new HashMap<String, String>();
        mutableConfig.put("host", "localhost");
        mutableConfig.put("port", "5432");
        mutableConfig.put("pool.max", "20");
        // Pass this around as immutable
        Map<String, String> config = Map.copyOf(mutableConfig);
        System.out.println("  Frozen config: " + config);

        // Pattern 4: Return unmodifiable from service — callers can't mutate your internal state
        System.out.println("  Returning unmodifiable collections from services prevents state leaks");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. String Handling Improvements
    // -------------------------------------------------------------------------
    static void stringHandling() {
        System.out.println("--- 4. String Handling Best Practices ---");

        // ✓ Use strip() over trim() for Unicode correctness
        var inputs = List.of("  Alice  ", "\tBob\t", "Carol", "  ", "");

        System.out.println("  Cleaning user input:");
        inputs.stream()
              .map(String::strip)
              .filter(Predicate.not(String::isBlank))
              .forEach(s -> System.out.println("    \"" + s + "\""));

        // ✓ Use lines() for multi-line processing — handles \r\n, \n, \r
        String report = "Header\r\nLine1\r\nLine2\r\nFooter";
        long lineCount = report.lines().count();
        System.out.println("  Lines (CRLF): " + lineCount);

        // ✓ Use repeat() for padding and decorators
        String title = " Java 11 Best Practices ";
        String bordered = "=" .repeat(title.length()) + "\n" + title + "\n" + "=".repeat(title.length());
        System.out.println("  " + bordered.replace("\n", "\n  "));

        // ✓ Use text blocks for multi-line literals (Java 15, but shown here)
        String json = """
                {
                    "name": "Alice",
                    "version": "11"
                }
                """;
        System.out.println("  Text block JSON (" + json.lines().count() + " lines)");

        // ✓ Use String.join / Collectors.joining for building delimited strings
        var names = List.of("Alice", "Bob", "Carol");
        System.out.println("  Joined: " + String.join(", ", names));
        System.out.println("  Formatted: " + names.stream()
                .collect(Collectors.joining(", ", "[", "]")));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. var Best Practices — when to use and when to avoid
    // -------------------------------------------------------------------------
    static void varBestPractices() {
        System.out.println("--- 5. var Best Practices ---");

        // ✓ Good: type is obvious from right-hand side
        var list = new ArrayList<String>();               // ArrayList is right there
        var entry = Map.entry("host", "localhost");       // clear from method name
        var path = Path.of("/tmp", "output.csv");         // Path.of is obvious

        // ✓ Good: avoids long generic spelling
        var byDept = new HashMap<String, List<Map<String, Object>>>();

        // ✗ Bad: type is not obvious from context
        // var result = process(config);    // what type is result?
        // var x = getManager();            // what type is Manager?

        System.out.println("  ✓ Good var uses: ArrayList, Map.entry, Path.of");

        // ✓ Good: for-loop variable — avoids verbose Iterator<Map.Entry<...>>
        var salaries = Map.of("Alice", 95000, "Bob", 82000, "Carol", 78000);
        for (var e : salaries.entrySet()) {
            // var inferred as Map.Entry<String, Integer>
        }

        // ✓ Good: try-with-resources
        // try (var reader = new BufferedReader(new FileReader(path))) { ... }

        // ✗ Avoid: var with primitives where confusion arises
        var i = 1;      // int (not long, not short — be explicit if it matters)
        var d = 1.0;    // double (not float)

        System.out.println("  var primitive: i=" + i + " (int), d=" + d + " (double)");

        // ✓ In lambda params: only to add an annotation
        // list.stream().filter((@NonNull var s) -> !s.isBlank())
        System.out.println("  var in lambdas: use when you need to add an annotation to the parameter");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Production JVM Reference
    // -------------------------------------------------------------------------
    static void productionJvmReference() {
        System.out.println("--- 6. Production JVM Configuration Reference ---");

        System.out.println(
            "  Recommended Java 11 production JVM flags:\n\n" +
            "  # Heap\n" +
            "  -Xms1g -Xmx1g                        # set equal — avoid heap resize pauses\n" +
            "  -XX:+UseG1GC                          # default since Java 9, explicit is fine\n" +
            "  -XX:MaxGCPauseMillis=200              # G1 pause target\n\n" +
            "  # Metaspace (replaced PermGen)\n" +
            "  -XX:MetaspaceSize=256m                # initial size — avoids early GC churn\n" +
            "  -XX:MaxMetaspaceSize=512m             # prevent native OOM on class leak\n\n" +
            "  # Container awareness (Kubernetes / Docker)\n" +
            "  -XX:+UseContainerSupport              # default ON in Java 11\n" +
            "  -XX:MaxRAMPercentage=75.0             # use 75% of container RAM as heap\n" +
            "  -XX:InitialRAMPercentage=50.0         # initial heap = 50% of container RAM\n\n" +
            "  # GC Logging\n" +
            "  -Xlog:gc*:file=gc.log:time,level:filecount=5,filesize=20m\n\n" +
            "  # Diagnostics\n" +
            "  -XX:+HeapDumpOnOutOfMemoryError\n" +
            "  -XX:HeapDumpPath=/var/log/\n" +
            "  -XX:+ExitOnOutOfMemoryError           # fail fast, let orchestrator restart\n\n" +
            "  # JFR always-on (< 1% overhead)\n" +
            "  -XX:StartFlightRecording=settings=default,disk=true,\\\n" +
            "      maxage=1h,maxsize=256m,name=continuous\n\n" +
            "  # Startup optimization\n" +
            "  -XX:TieredStopAtLevel=1               # fast startup (interpreted only) for CLIs\n" +
            "  (remove this for long-running services — it disables JIT optimization)\n"
        );

        // Print what's actually set
        System.out.println("  Current JVM:");
        System.out.println("    Heap max: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        System.out.println("    CPUs:     " + Runtime.getRuntime().availableProcessors());
        System.out.println("    Version:  " + Runtime.version());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Java 8 vs Java 11 Performance Notes
    // -------------------------------------------------------------------------
    static void performanceComparison() {
        System.out.println("--- 7. Java 8 vs Java 11 Performance Notes ---");

        System.out.println(
            "  Startup time:\n" +
            "    Java 11 starts 10-30% faster than Java 8 for most Spring Boot apps\n" +
            "    due to AppCDS and faster class loading.\n\n" +
            "  Throughput:\n" +
            "    G1GC (Java 11 default) vs ParallelGC (Java 8 default):\n" +
            "    G1GC trades ~5-10% peak throughput for better pause time predictability.\n" +
            "    If you have batch jobs that maximise throughput: -XX:+UseParallelGC\n\n" +
            "  String performance:\n" +
            "    Java 9+ uses Compact Strings — Latin-1 strings use 1 byte/char instead of 2.\n" +
            "    Most apps see 10-20% less heap for String data.\n" +
            "    -XX:-CompactStrings to disable (not recommended).\n\n" +
            "  Container sizing:\n" +
            "    Java 11 respects Docker --memory limits by default (not Java 8).\n" +
            "    On Java 8, set -Xmx explicitly. On Java 11, use -XX:MaxRAMPercentage.\n"
        );

        // Quick allocation rate demo
        long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        var strings = IntStream.range(0, 10_000)
                .mapToObj(i -> "employee-" + i + "@company.com")
                .collect(Collectors.toList());
        long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.out.printf("  Allocated %,d strings: %+.1f KB heap delta%n",
                strings.size(), (after - before) / 1024.0);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 8. Security Hardening Checklist
    // -------------------------------------------------------------------------
    static void securityChecklist() {
        System.out.println("--- 8. Security Hardening Checklist ---");

        System.out.println(
            "  TLS:\n" +
            "    ✓ Use TLS 1.2 minimum, TLS 1.3 preferred\n" +
            "    ✓ Disable TLSv1.0, TLSv1.1 in java.security:\n" +
            "        jdk.tls.disabledAlgorithms=SSLv3,TLSv1,TLSv1.1,...\n" +
            "    ✓ Use AES-256-GCM or ChaCha20-Poly1305 for symmetric encryption\n" +
            "    ✓ Use Curve25519 (X25519) for key exchange\n" +
            "    ✓ Rotate certificates before expiry (monitor via JFR or Prometheus)\n\n" +
            "  Password / Secrets:\n" +
            "    ✓ Hash passwords with PBKDF2WithHmacSHA256 (310k iterations) or Argon2\n" +
            "    ✓ Never log passwords, tokens, or PII\n" +
            "    ✓ Use SecureRandom for all security-sensitive randomness\n" +
            "    ✓ Store secrets in Vault / AWS Secrets Manager — not in config files\n\n" +
            "  JVM security:\n" +
            "    ✓ Run as non-root user (UID ≠ 0) in containers\n" +
            "    ✓ Use Java security manager policies for sandboxed code (Java 11; removed Java 17+)\n" +
            "    ✓ Enable JFR for audit: custom @Event for authentication, authorisation\n" +
            "    ✓ Keep JDK updated — subscribe to https://jdk.java.net/security-advisories/\n\n" +
            "  Dependencies:\n" +
            "    ✓ Run OWASP Dependency Check or Snyk on every build\n" +
            "    ✓ Lock transitive dependency versions (dependency locking in Gradle)\n" +
            "    ✓ Minimise attack surface — use jlink to exclude unused JDK modules\n"
        );

        // Show current security properties relevant to TLS
        System.out.println("  java.security disabled algorithms (first 3):");
        var disabled = java.security.Security.getProperty("jdk.tls.disabledAlgorithms");
        if (disabled != null) {
            Arrays.stream(disabled.split(",")).limit(3)
                  .forEach(a -> System.out.println("    " + a.strip()));
        }
    }
}

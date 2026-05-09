package com.devopsmonk.java11.ch06_collections;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chapter 06 — Collection Factory Methods (JEP 269): List.of, Set.of, Map.of
 * Tutorial: https://devops-monk.com/tutorials/java11/collection-factory-methods/
 *
 * Java 9 added static factory methods to List, Set, and Map that return
 * compact, immutable collections. Java 10 added copyOf() to all three.
 *
 * Key properties of factory-created collections:
 *   - Immutable (add/remove/set throw UnsupportedOperationException)
 *   - Null-hostile (null elements/keys/values throw NullPointerException)
 *   - Serializable
 *   - Compact memory layout (no backing array overhead for small sizes)
 *   - Set and Map make no guarantee about iteration order
 */
public class CollectionFactoryExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 06: Collection Factory Methods ===\n");

        listOf();
        setOf();
        mapOf();
        mapOfEntries();
        copyOf();
        nullHostility();
        immutabilityGuarantee();
        realWorldUsage();
    }

    // -------------------------------------------------------------------------
    // 1. List.of() — ordered, immutable, allows duplicates
    // -------------------------------------------------------------------------
    static void listOf() {
        System.out.println("--- 1. List.of() ---");

        // Before Java 9 options:
        // Arrays.asList("a", "b") — fixed-size but mutable (set() works, add() doesn't)
        // Collections.unmodifiableList(new ArrayList<>(...)) — verbose

        // Java 9+: clean and truly immutable
        var empty   = List.of();
        var single  = List.of("Alice");
        var roles   = List.of("ADMIN", "USER", "VIEWER");
        var numbers = List.of(1, 2, 3, 4, 5);

        System.out.println("  empty:   " + empty + "  size=" + empty.size());
        System.out.println("  single:  " + single);
        System.out.println("  roles:   " + roles);
        System.out.println("  numbers: " + numbers);

        // Preserves insertion order (unlike Set.of)
        var ordered = List.of("first", "second", "third");
        System.out.println("  Order preserved: " + ordered.get(0) + ", " + ordered.get(1));

        // Allows duplicates (unlike Set.of)
        var withDupes = List.of("java", "java", "streams");
        System.out.println("  Duplicates allowed: " + withDupes);

        // Iteration
        System.out.print("  Iterating: ");
        for (var s : roles) System.out.print(s + " ");
        System.out.println();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Set.of() — unordered, immutable, no duplicates (throws on dupes)
    // -------------------------------------------------------------------------
    static void setOf() {
        System.out.println("--- 2. Set.of() ---");

        var permissions = Set.of("READ", "WRITE", "EXECUTE");
        var tags        = Set.of("java", "jvm", "backend");

        System.out.println("  permissions: " + permissions);  // order not guaranteed
        System.out.println("  tags:        " + tags);
        System.out.println("  contains READ: " + permissions.contains("READ"));
        System.out.println("  contains DELETE: " + permissions.contains("DELETE"));

        // Efficient membership test — use Set, not List, for lookup
        var allowedDepts = Set.of("ENGINEERING", "PRODUCT", "DESIGN");
        var requestDept = "MARKETING";
        System.out.println("  " + requestDept + " allowed: " + allowedDepts.contains(requestDept));

        // Duplicate detection at construction time — throws IllegalArgumentException
        try {
            var bad = Set.of("java", "java");  // duplicate!
        } catch (IllegalArgumentException e) {
            System.out.println("  Duplicate detected at creation: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Map.of() — up to 10 key-value pairs
    // -------------------------------------------------------------------------
    static void mapOf() {
        System.out.println("--- 3. Map.of() ---");

        // Alternating key, value pairs — up to 10 pairs (20 args)
        var httpStatus = Map.of(
                200, "OK",
                201, "Created",
                204, "No Content",
                400, "Bad Request",
                401, "Unauthorized",
                403, "Forbidden",
                404, "Not Found",
                500, "Internal Server Error"
        );

        System.out.println("  200 → " + httpStatus.get(200));
        System.out.println("  404 → " + httpStatus.get(404));
        System.out.println("  Size: " + httpStatus.size());

        // Config map
        var dbConfig = Map.of(
                "host",     "db.prod.internal",
                "port",     "5432",
                "database", "employees",
                "pool.min", "5",
                "pool.max", "20"
        );
        System.out.println("  DB host: " + dbConfig.get("host") + ":" + dbConfig.get("port"));

        // getOrDefault still works
        System.out.println("  timeout (default): " + dbConfig.getOrDefault("timeout", "30"));

        // Duplicate key detection
        try {
            var bad = Map.of("key", "v1", "key", "v2");
        } catch (IllegalArgumentException e) {
            System.out.println("  Duplicate key detected: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Map.ofEntries() — for more than 10 entries
    // -------------------------------------------------------------------------
    static void mapOfEntries() {
        System.out.println("--- 4. Map.ofEntries() ---");

        // For > 10 pairs — use Map.entry() (also Java 9)
        var salaryBands = Map.ofEntries(
                Map.entry("JUNIOR",    40_000),
                Map.entry("MID",       65_000),
                Map.entry("SENIOR",    90_000),
                Map.entry("STAFF",    115_000),
                Map.entry("PRINCIPAL",140_000),
                Map.entry("DIRECTOR", 175_000),
                Map.entry("VP",       220_000),
                Map.entry("SVP",      280_000),
                Map.entry("EVP",      350_000),
                Map.entry("CEO",      500_000),
                Map.entry("CTO",      450_000)  // 11th entry — needs ofEntries
        );

        System.out.println("  Salary bands loaded: " + salaryBands.size());
        System.out.println("  SENIOR band: £" + String.format("%,d", salaryBands.get("SENIOR")));

        // Sort and print
        salaryBands.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(4)
                .forEach(e -> System.out.printf("    %-10s £%,d%n", e.getKey(), e.getValue()));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. copyOf() — immutable copy of an existing collection (Java 10)
    // -------------------------------------------------------------------------
    static void copyOf() {
        System.out.println("--- 5. copyOf() (Java 10) ---");

        var mutableList = new ArrayList<>(List.of("Alice", "Bob", "Carol"));
        mutableList.add("Dave");

        // Take an immutable snapshot
        var snapshot = List.copyOf(mutableList);
        mutableList.add("Eve");  // mutate original — snapshot is unaffected

        System.out.println("  mutable: " + mutableList);
        System.out.println("  snapshot: " + snapshot);  // still 4 elements

        // copyOf on a Set
        var mutableSet = new HashSet<>(Set.of("READ", "WRITE"));
        mutableSet.add("DELETE");
        var immutableSet = Set.copyOf(mutableSet);
        System.out.println("  immutableSet: " + immutableSet);

        // copyOf on a Map
        var mutableMap = new HashMap<>(Map.of("a", 1, "b", 2));
        mutableMap.put("c", 3);
        var immutableMap = Map.copyOf(mutableMap);
        System.out.println("  immutableMap: " + immutableMap);

        // If input is already immutable (List.of), copyOf may return the same instance
        var original  = List.of("x", "y");
        var copy      = List.copyOf(original);
        System.out.println("  Same instance when already immutable: " + (original == copy));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Null Hostility
    // -------------------------------------------------------------------------
    static void nullHostility() {
        System.out.println("--- 6. Null Hostility ---");

        // All factory methods reject null at construction time
        System.out.println("  Testing null rejection:");
        tryNullList();
        tryNullSet();
        tryNullMapKey();
        tryNullMapValue();

        System.out.println("  Use Optional or a sentinel value instead of null in immutable collections.");
        System.out.println();
    }

    static void tryNullList() {
        try { List.of("a", null, "c"); }
        catch (NullPointerException e) { System.out.println("  List.of with null → NullPointerException"); }
    }
    static void tryNullSet() {
        try { Set.of("a", null); }
        catch (NullPointerException e) { System.out.println("  Set.of with null → NullPointerException"); }
    }
    static void tryNullMapKey() {
        try { Map.of(null, "value"); }
        catch (NullPointerException e) { System.out.println("  Map.of null key → NullPointerException"); }
    }
    static void tryNullMapValue() {
        try { Map.of("key", null); }
        catch (NullPointerException e) { System.out.println("  Map.of null value → NullPointerException"); }
    }

    // -------------------------------------------------------------------------
    // 7. Immutability Guarantee
    // -------------------------------------------------------------------------
    static void immutabilityGuarantee() {
        System.out.println("--- 7. Immutability ---");

        var list = List.of("Alice", "Bob");
        var set  = Set.of("READ", "WRITE");
        var map  = Map.of("host", "localhost");

        tryMutate("list.add",    () -> ((List<String>)(Object)list).add("Carol"));
        tryMutate("list.remove", () -> ((List<String>)(Object)list).remove(0));
        tryMutate("set.add",     () -> ((Set<String>)(Object)set).add("EXECUTE"));
        tryMutate("map.put",     () -> ((Map<String,String>)(Object)map).put("port", "5432"));

        System.out.println();
    }

    @SuppressWarnings("unchecked")
    static void tryMutate(String label, Runnable op) {
        try { op.run(); System.out.println("  " + label + " succeeded (unexpected!)"); }
        catch (UnsupportedOperationException e) {
            System.out.println("  " + label + " → UnsupportedOperationException ✓");
        }
    }

    // -------------------------------------------------------------------------
    // 8. Real-World Usage
    // -------------------------------------------------------------------------
    static void realWorldUsage() {
        System.out.println("--- 8. Real-World Patterns ---");

        // Pattern 1: Constant lookup tables — replace static final Maps with
        // null-safe, immutable, compact factories
        var COUNTRY_CODES = Map.of(
                "UK", "GBR", "US", "USA", "DE", "DEU", "FR", "FRA"
        );
        System.out.println("  Country: " + COUNTRY_CODES.getOrDefault("UK", "UNK"));

        // Pattern 2: Allowlists / denylists
        var BLOCKED_EXTENSIONS = Set.of(".exe", ".bat", ".sh", ".ps1");
        String upload = "report.pdf";
        boolean blocked = BLOCKED_EXTENSIONS.stream()
                .anyMatch(ext -> upload.endsWith(ext));
        System.out.println("  " + upload + " blocked: " + blocked);

        String badUpload = "malware.exe";
        System.out.println("  " + badUpload + " blocked: " +
                BLOCKED_EXTENSIONS.stream().anyMatch(badUpload::endsWith));

        // Pattern 3: Default config with mutable override
        var defaults = Map.of("timeout", "30", "retries", "3", "pool.max", "10");
        var overrides = Map.of("pool.max", "20", "debug", "true");

        // Merge: overrides win
        var merged = new HashMap<>(defaults);
        merged.putAll(overrides);
        var config = Map.copyOf(merged);  // freeze after merging
        System.out.println("  Merged config pool.max: " + config.get("pool.max"));
        System.out.println("  Merged config timeout:  " + config.get("timeout"));

        // Pattern 4: Return immutable view from service
        var activeRoles = List.of("USER", "VIEWER");
        System.out.println("  Active roles: " + activeRoles);
    }
}

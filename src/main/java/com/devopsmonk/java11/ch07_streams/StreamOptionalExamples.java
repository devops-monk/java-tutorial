package com.devopsmonk.java11.ch07_streams;

import java.util.*;
import java.util.stream.*;

/**
 * Chapter 07 — Stream & Optional Enhancements (Java 9–11)
 * Tutorial: https://devops-monk.com/tutorials/java11/stream-optional-enhancements/
 *
 * Stream additions:
 *   Java 9:  takeWhile, dropWhile, Stream.ofNullable, Stream.iterate (with predicate)
 *   Java 9:  Collectors.filtering, Collectors.flatMapping
 *   Java 10: Collectors.toUnmodifiableList/Set/Map
 *   Java 11: (no new Stream methods, but Predicate.not added)
 *
 * Optional additions:
 *   Java 9:  ifPresentOrElse, or, stream
 *   Java 10: isEmpty (inverse of isPresent)
 *   Java 11: orElseThrow() — no-arg version
 */
public class StreamOptionalExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 07: Stream & Optional Enhancements ===\n");

        takeWhileAndDropWhile();
        ofNullable();
        iterateWithPredicate();
        collectorsFiltering();
        collectorsToUnmodifiable();
        optionalIfPresentOrElse();
        optionalOr();
        optionalStream();
        optionalIsEmpty();
        predicateNot();
    }

    // -------------------------------------------------------------------------
    // 1. takeWhile / dropWhile — process ordered streams conditionally
    // -------------------------------------------------------------------------
    static void takeWhileAndDropWhile() {
        System.out.println("--- 1. takeWhile / dropWhile ---");

        var salaries = List.of(45_000, 62_000, 78_000, 91_000, 115_000, 130_000);

        // takeWhile — take elements while predicate holds; stop at first failure
        var affordable = salaries.stream()
                .takeWhile(s -> s < 100_000)
                .collect(Collectors.toList());
        System.out.println("  takeWhile(< 100k): " + affordable);

        // dropWhile — skip elements while predicate holds; take the rest
        var highEarners = salaries.stream()
                .dropWhile(s -> s < 100_000)
                .collect(Collectors.toList());
        System.out.println("  dropWhile(< 100k): " + highEarners);

        // Unlike filter, takeWhile/dropWhile stop at the first mismatch
        // IMPORTANT: designed for ORDERED streams — behaviour on unordered is non-deterministic
        var words = List.of("alpha", "beta", "gamma", "delta", "epsilon");
        var shortWords = words.stream()
                .takeWhile(w -> w.length() <= 5)
                .collect(Collectors.toList());
        System.out.println("  Short words (takeWhile length≤5): " + shortWords);
        // "delta" would pass but takeWhile stops at "gamma" (length 5 → boundary)

        // Real-world: process a sorted price list up to a budget
        var priceList = List.of(9.99, 24.99, 49.99, 99.99, 149.99, 299.99);
        double budget = 100.0;
        var withinBudget = priceList.stream()
                .takeWhile(p -> p <= budget)
                .collect(Collectors.toList());
        System.out.println("  Items within £" + budget + " budget: " + withinBudget);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Stream.ofNullable — avoids null check before streaming
    // -------------------------------------------------------------------------
    static void ofNullable() {
        System.out.println("--- 2. Stream.ofNullable ---");

        // Java 8 pattern to handle nullable single element:
        // String value = getValue(); // may be null
        // (value != null ? Stream.of(value) : Stream.empty())

        // Java 9+: one call handles both cases
        String present = "hello";
        String absent  = null;

        Stream.ofNullable(present).forEach(s -> System.out.println("  present: " + s));
        Stream.ofNullable(absent).forEach(s -> System.out.println("  absent: " + s));  // no output
        System.out.println("  absent count: " + Stream.ofNullable(absent).count());

        // Real-world: safely flatMap a nullable field
        var records = List.of(
                Map.of("name", "Alice", "email", "alice@example.com"),
                Map.of("name", "Bob"),           // no email key
                Map.of("name", "Carol", "email", "carol@example.com")
        );

        var emails = records.stream()
                .flatMap(r -> Stream.ofNullable(r.get("email")))
                .collect(Collectors.toList());
        System.out.println("  Emails (null-safe flatMap): " + emails);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Stream.iterate with predicate (Java 9)
    // -------------------------------------------------------------------------
    static void iterateWithPredicate() {
        System.out.println("--- 3. Stream.iterate with predicate ---");

        // Java 8: iterate(seed, f) — infinite stream, always needs limit()
        var powersOf2_java8 = Stream.iterate(1, n -> n * 2)
                .limit(8)
                .collect(Collectors.toList());
        System.out.println("  Powers of 2 (Java 8 iterate): " + powersOf2_java8);

        // Java 9: iterate(seed, hasNext, f) — built-in stopping condition
        var powersOf2_java9 = Stream.iterate(1, n -> n <= 128, n -> n * 2)
                .collect(Collectors.toList());
        System.out.println("  Powers of 2 (Java 9 iterate): " + powersOf2_java9);

        // Fibonacci sequence up to 100
        var fibonacci = Stream.iterate(
                new long[]{0, 1},
                f -> f[0] <= 100,
                f -> new long[]{f[1], f[0] + f[1]}
        )
        .map(f -> f[0])
        .collect(Collectors.toList());
        System.out.println("  Fibonacci ≤ 100: " + fibonacci);

        // Date range — iterate over LocalDate
        var today = java.time.LocalDate.of(2024, 1, 15);
        var nextFiveDays = Stream.iterate(today, d -> d.isBefore(today.plusDays(5)), d -> d.plusDays(1))
                .collect(Collectors.toList());
        System.out.println("  Next 5 days: " + nextFiveDays);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Collectors.filtering / Collectors.flatMapping (Java 9)
    // -------------------------------------------------------------------------
    static void collectorsFiltering() {
        System.out.println("--- 4. Collectors.filtering / flatMapping ---");

        record Employee(String name, String dept, int salary) {}
        var employees = List.of(
                new Employee("Alice", "ENG",   95000),
                new Employee("Bob",   "PROD",  82000),
                new Employee("Carol", "ENG",   91000),
                new Employee("Dave",  "ENG",   75000),
                new Employee("Eve",   "PROD",  88000)
        );

        // Collectors.filtering — filter INSIDE a downstream collector
        // Use case: groupBy with filtering per group (vs filtering the whole stream first)
        var highEarnersByDept = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::dept,
                        Collectors.filtering(e -> e.salary() > 85_000,
                                Collectors.mapping(Employee::name, Collectors.toList()))
                ));
        System.out.println("  High earners by dept (>£85k): " + highEarnersByDept);

        // Collectors.flatMapping — flatMap inside a downstream collector
        record Team(String name, List<String> members) {}
        var teams = List.of(
                new Team("Backend",  List.of("Alice", "Dave")),
                new Team("Frontend", List.of("Bob", "Carol")),
                new Team("DevOps",   List.of("Eve"))
        );

        // All members, grouped by first letter of team name
        var membersByTeamInitial = teams.stream()
                .collect(Collectors.groupingBy(
                        t -> t.name().charAt(0),
                        Collectors.flatMapping(t -> t.members().stream(), Collectors.toList())
                ));
        System.out.println("  Members by team initial: " + membersByTeamInitial);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Collectors.toUnmodifiableList/Set/Map (Java 10)
    // -------------------------------------------------------------------------
    static void collectorsToUnmodifiable() {
        System.out.println("--- 5. Collectors.toUnmodifiable* (Java 10) ---");

        var names = List.of("alice", "bob", "carol", "dave");

        // Java 8 idiom — extra wrap
        // Collections.unmodifiableList(names.stream().map(...).collect(Collectors.toList()))

        // Java 10 — direct
        var upperNames = names.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableList());
        System.out.println("  toUnmodifiableList: " + upperNames);

        var uniqueLengths = names.stream()
                .map(String::length)
                .collect(Collectors.toUnmodifiableSet());
        System.out.println("  toUnmodifiableSet: " + uniqueLengths);

        var nameToLength = names.stream()
                .collect(Collectors.toUnmodifiableMap(n -> n, String::length));
        System.out.println("  toUnmodifiableMap: " + nameToLength);

        // Verify immutability
        try {
            upperNames.add("EVE");
        } catch (UnsupportedOperationException e) {
            System.out.println("  Correctly immutable: UnsupportedOperationException");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Optional.ifPresentOrElse (Java 9)
    // -------------------------------------------------------------------------
    static void optionalIfPresentOrElse() {
        System.out.println("--- 6. Optional.ifPresentOrElse (Java 9) ---");

        // Java 8: had to check and branch manually
        Optional<String> found   = Optional.of("Alice");
        Optional<String> missing = Optional.empty();

        // Java 9: single method for both branches
        found.ifPresentOrElse(
                name -> System.out.println("  Found: " + name),
                ()   -> System.out.println("  Not found")
        );

        missing.ifPresentOrElse(
                name -> System.out.println("  Found: " + name),
                ()   -> System.out.println("  Not found — using default")
        );

        // Real-world: audit logging with two code paths
        Optional<String> cachedValue = Optional.empty();
        cachedValue.ifPresentOrElse(
                v -> System.out.println("  Cache hit: " + v),
                () -> System.out.println("  Cache miss — loading from DB")
        );

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Optional.or (Java 9) — chain fallback Optionals
    // -------------------------------------------------------------------------
    static void optionalOr() {
        System.out.println("--- 7. Optional.or (Java 9) ---");

        // orElseGet returns a value; or returns another Optional
        // Useful for chaining multiple optional sources

        Optional<String> primary   = Optional.empty();
        Optional<String> secondary = Optional.of("fallback-from-secondary");

        // Java 8 pattern — awkward
        // Optional<String> result = primary.isPresent() ? primary : secondary;

        // Java 9: clean chaining
        var result = primary.or(() -> secondary);
        System.out.println("  primary.or(secondary): " + result);

        // Chain multiple sources
        Optional<String> source1 = Optional.empty();
        Optional<String> source2 = Optional.empty();
        Optional<String> source3 = Optional.of("found in source3");

        var found = source1
                .or(() -> source2)
                .or(() -> source3);
        System.out.println("  Chain of 3 sources: " + found.orElse("none"));

        // Practical: config lookup priority (env var → system prop → default)
        Optional<String> fromEnv  = Optional.ofNullable(System.getenv("DB_HOST"));
        Optional<String> fromProp = Optional.ofNullable(System.getProperty("db.host"));
        String host = fromEnv
                .or(() -> fromProp)
                .orElse("localhost");
        System.out.println("  DB_HOST: " + host);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 8. Optional.stream (Java 9) — bridge Optional into Stream pipelines
    // -------------------------------------------------------------------------
    static void optionalStream() {
        System.out.println("--- 8. Optional.stream (Java 9) ---");

        // Before: filtering Optionals in a stream was ugly
        // list.stream()
        //     .map(this::findById)       // returns Optional<T>
        //     .filter(Optional::isPresent)
        //     .map(Optional::get)        // unsafe without the filter above
        //     ...

        // Java 9: flatMap with Optional.stream — clean and safe
        var ids = List.of(1, 2, 3, 99, 4, 100);  // 99 and 100 don't exist

        var found = ids.stream()
                .map(id -> findEmployee(id))   // returns Optional<String>
                .flatMap(Optional::stream)      // flattens: empty() → nothing, of(x) → x
                .collect(Collectors.toList());
        System.out.println("  Employees found: " + found);

        System.out.println();
    }

    static Optional<String> findEmployee(int id) {
        var db = Map.of(1, "Alice", 2, "Bob", 3, "Carol", 4, "Dave");
        return Optional.ofNullable(db.get(id));
    }

    // -------------------------------------------------------------------------
    // 9. Optional.isEmpty (Java 11)
    // -------------------------------------------------------------------------
    static void optionalIsEmpty() {
        System.out.println("--- 9. Optional.isEmpty (Java 11) ---");

        Optional<String> present = Optional.of("hello");
        Optional<String> empty   = Optional.empty();

        // Java 8: !opt.isPresent() — double negation, easy to misread
        System.out.println("  !isPresent(): " + !present.isPresent());

        // Java 11: isEmpty() — reads naturally
        System.out.println("  present.isEmpty(): " + present.isEmpty());
        System.out.println("  empty.isEmpty():   " + empty.isEmpty());

        // In a stream filter
        var optionals = List.of(Optional.of("Alice"), Optional.empty(),
                                Optional.of("Bob"), Optional.empty());
        long emptyCount = optionals.stream().filter(Optional::isEmpty).count();
        System.out.println("  Empty optionals in list: " + emptyCount);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 10. Predicate.not (Java 11) — negate a method reference cleanly
    // -------------------------------------------------------------------------
    static void predicateNot() {
        System.out.println("--- 10. Predicate.not (Java 11) ---");

        var lines = List.of("Alice,ENG,95000", "", "  ", "Bob,PROD,82000", "\t");

        // Java 8: e -> !e.isBlank() — lambda negation
        var nonBlank8 = lines.stream()
                .filter(l -> !l.isBlank())
                .collect(Collectors.toList());

        // Java 11: Predicate.not — method reference style (composable, annotatable)
        var nonBlank11 = lines.stream()
                .filter(Predicate.not(String::isBlank))
                .collect(Collectors.toList());

        System.out.println("  Java 8  filter(!isBlank): " + nonBlank8);
        System.out.println("  Java 11 Predicate.not:    " + nonBlank11);

        // Composing: not + and + or
        var optionals = List.of(Optional.of("Alice"), Optional.empty(), Optional.of("Bob"));
        var presentValues = optionals.stream()
                .filter(Predicate.not(Optional::isEmpty))
                .map(Optional::get)
                .collect(Collectors.toList());
        System.out.println("  Present Optional values: " + presentValues);

        System.out.println();
    }
}

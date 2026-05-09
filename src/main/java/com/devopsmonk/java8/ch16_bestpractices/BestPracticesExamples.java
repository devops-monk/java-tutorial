package com.devopsmonk.java8.ch16_bestpractices;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;
import com.devopsmonk.java8.model.Order;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.stream.*;

/**
 * Chapter 16 — Java 8 Best Practices and Patterns for Production Code
 * Tutorial: https://devops-monk.com/tutorials/java8/best-practices/
 *
 * Each section shows a BEFORE (Java 7 / bad habit) and an AFTER (idiomatic Java 8),
 * with commentary on why the Java 8 version is better.
 */
public class BestPracticesExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 16: Best Practices ===\n");

        streamDoAndDont();
        optionalBestPractices();
        lambdaReadability();
        completableFutureBestPractices();
        methodReferenceGuidelines();
        immutabilityAndSideEffects();
        migrationChecklist();
    }

    // -------------------------------------------------------------------------
    // 1. Stream Dos and Don'ts
    // -------------------------------------------------------------------------
    static void streamDoAndDont() {
        System.out.println("--- 1. Stream Dos and Don'ts ---");

        List<Employee> employees = Employee.SampleData.employees();

        // ✗ DON'T: mix streams and mutation of external state
        List<String> badResult = new ArrayList<>();
        employees.stream()
                 .filter(Employee::isActive)
                 .forEach(e -> badResult.add(e.getName())); // mutation in stream — breaks with parallel
        // The above works in sequential but is a bad habit and breaks with .parallel()

        // ✓ DO: use collect() — thread-safe, declarative, works in parallel
        List<String> goodResult = employees.stream()
                .filter(Employee::isActive)
                .map(Employee::getName)
                .collect(Collectors.toList());
        System.out.println("Active names (good): " + goodResult.size());

        // ✗ DON'T: create a stream and not use it (streams are single-use)
        Stream<Employee> stream = employees.stream().filter(Employee::isActive);
        long count1 = stream.count();
        // stream.count(); // IllegalStateException: stream has already been operated upon
        System.out.println("Count (single use): " + count1);

        // ✓ DO: create a Supplier<Stream<T>> if you need to reuse
        Supplier<Stream<Employee>> activeEmployees = () ->
                employees.stream().filter(Employee::isActive);
        System.out.println("Count via supplier: " + activeEmployees.get().count());
        System.out.println("Max salary:         £" + (int) activeEmployees.get()
                .mapToDouble(Employee::getSalary).max().orElse(0));

        // ✗ DON'T: use primitive-boxed streams for numeric work
        // Stream<Integer> — boxing/unboxing on every element
        OptionalDouble avgBoxed = employees.stream()
                .map(Employee::getSalary)              // Stream<Double> — boxed
                .mapToDouble(Double::doubleValue)      // unbox immediately
                .average();

        // ✓ DO: use mapToDouble/mapToInt/mapToLong directly
        OptionalDouble avgPrimitive = employees.stream()
                .mapToDouble(Employee::getSalary)      // IntStream — no boxing
                .average();
        System.out.printf("Avg salary: £%.0f%n", avgPrimitive.orElse(0));

        // ✗ DON'T: use streams for simple loops with one transformation
        // employees.stream().forEach(e -> System.out.println(e)); // forEach on a loop is cleaner
        // ✓ DO: use streams when you have a pipeline (filter + map + collect)

        // ✓ DO: use method references over lambdas where they read naturally
        employees.stream()
                 .map(Employee::getName)      // cleaner than e -> e.getName()
                 .forEach(System.out::println); // cleaner than name -> System.out.println(name)

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Optional Best Practices
    // -------------------------------------------------------------------------
    static void optionalBestPractices() {
        System.out.println("--- 2. Optional Best Practices ---");

        List<Employee> employees = Employee.SampleData.employees();

        // ✓ DO: return Optional from methods that may have no result
        Optional<Employee> topEarner = findTopEarner(employees, Department.ENGINEERING);
        topEarner.ifPresent(e -> System.out.println("Top engineer: " + e.getName()));

        // ✓ DO: chain map/filter/orElse — never call get() without isPresent()
        String topEarnerReport = findTopEarner(employees, Department.ENGINEERING)
                .map(e -> String.format("%s earns £%.0f", e.getName(), e.getSalary()))
                .orElse("No engineers found");
        System.out.println(topEarnerReport);

        // ✓ DO: use orElseGet (lazy) over orElse (eager) for expensive defaults
        Employee assignee = findTopEarner(employees, Department.DESIGN)
                .orElseGet(() -> employees.stream()
                        .filter(Employee::isActive)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No active employees")));
        System.out.println("Assignee: " + assignee.getName());

        // ✗ DON'T: use Optional as a method parameter — use method overloading or null check instead
        // void process(Optional<String> name) — BAD: forces callers to wrap

        // ✗ DON'T: use Optional in entity fields — it is not Serializable
        // ✗ DON'T: return Optional<List<T>> — return an empty list instead

        // ✗ DON'T: orElse(null) — defeats the purpose of Optional
        // Employee e = opt.orElse(null);  // just use nullable return type

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Lambda Readability Rules
    // -------------------------------------------------------------------------
    static void lambdaReadability() {
        System.out.println("--- 3. Lambda Readability ---");

        List<Employee> employees = Employee.SampleData.employees();

        // ✓ DO: extract complex lambdas to named methods (or named variables)
        // BAD — inline lambda with too much logic:
        employees.stream()
                 .filter(e -> e.isActive() && e.getSalary() > 90_000 && e.getJoinDate().isBefore(LocalDate.of(2020, 1, 1)))
                 .map(e -> String.format("%-15s £%.0f (%d yr)", e.getName(), e.getSalary(), e.getJoinDate().until(LocalDate.now()).getYears()))
                 .forEach(System.out::println);

        // GOOD — extract to named predicates and formatters:
        Predicate<Employee> isSeniorHighEarner = BestPracticesExamples::isSeniorHighEarner;
        Function<Employee, String> toReport = BestPracticesExamples::formatEmployeeReport;

        employees.stream()
                 .filter(isSeniorHighEarner)
                 .map(toReport)
                 .forEach(System.out::println);

        // ✓ DO: keep lambdas short — if it needs multiple lines, it needs a method
        // One expression: good
        Comparator<Employee> bySalary = Comparator.comparingDouble(Employee::getSalary);

        // ✓ DO: use block-body lambdas only when truly necessary
        // Three or more statements → extract to a private method

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. CompletableFuture Best Practices
    // -------------------------------------------------------------------------
    static void completableFutureBestPractices() throws Exception {
        System.out.println("--- 4. CompletableFuture Best Practices ---");

        // ✓ DO: always handle exceptions — unhandled exceptions in async pipelines are silent
        CompletableFuture<String> safe = CompletableFuture
                .supplyAsync(() -> fetchData())
                .exceptionally(ex -> "fallback: " + ex.getMessage())
                .thenApply(String::toUpperCase);
        System.out.println("Safe result: " + safe.get());

        // ✗ DON'T: call .get() without timeout — it blocks forever if the future hangs
        try {
            safe.get(5, java.util.concurrent.TimeUnit.SECONDS); // always set a timeout
        } catch (java.util.concurrent.TimeoutException e) {
            System.out.println("Timed out — circuit-break here");
        }

        // ✓ DO: use thenCompose (not thenApply) when the next step is also async
        CompletableFuture<String> composed = CompletableFuture
                .supplyAsync(() -> "step1")
                .thenCompose(s -> CompletableFuture.supplyAsync(() -> s + "-step2")); // flat
        System.out.println("Composed: " + composed.get());

        // ✓ DO: use allOf to run independent tasks in parallel, not sequentially
        CompletableFuture<Void> parallel = CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> {}),  // task A
                CompletableFuture.runAsync(() -> {}),  // task B
                CompletableFuture.runAsync(() -> {})   // task C — all run concurrently
        );
        parallel.get();
        System.out.println("All parallel tasks done.");

        // ✓ DO: use a dedicated thread pool for CPU-bound or I/O-bound work
        // instead of relying on ForkJoinPool.commonPool() which is shared

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Method Reference Guidelines
    // -------------------------------------------------------------------------
    static void methodReferenceGuidelines() {
        System.out.println("--- 5. Method Reference Guidelines ---");

        List<Employee> employees = Employee.SampleData.employees();

        // ✓ DO: use method references when they read like natural English
        employees.stream()
                 .filter(Employee::isActive)       // "is active" — reads well
                 .map(Employee::getName)            // "get name" — reads well
                 .forEach(System.out::println);     // "print" — reads well

        // ✗ DON'T: force a method reference when a lambda is clearer
        // employees.stream().filter(Objects::nonNull) -- fine
        // employees.stream().map(e -> e.getSalary() * 1.1) -- lambda is clearer than a helper method

        // ✗ DON'T: use method references that require casting or are ambiguous
        // e.g. when there are multiple overloads of the target method

        // ✓ DO: extract a named helper method when logic is complex
        employees.stream()
                 .map(BestPracticesExamples::toPayslip)  // named helper — clear intent
                 .limit(3)
                 .forEach(System.out::println);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Immutability and Avoiding Side Effects in Streams
    // -------------------------------------------------------------------------
    static void immutabilityAndSideEffects() {
        System.out.println("--- 6. Immutability and Side Effects ---");

        List<Employee> employees = Employee.SampleData.employees();

        // ✗ DON'T: sort the original list inside a stream pipeline
        // employees.sort(...) is a side effect — it mutates the list
        // and breaks other code holding a reference to it

        // ✓ DO: collect into a new sorted list
        List<Employee> sortedCopy = employees.stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .collect(Collectors.toList());
        System.out.println("Original first: " + employees.get(0).getName() + " (unchanged)");
        System.out.println("Sorted first:   " + sortedCopy.get(0).getName() + " (highest earner)");

        // ✓ DO: make stream operations stateless — no shared mutable state
        // ✗ DON'T modify elements while streaming them

        // ✓ DO: use unmodifiable collections for return types from services
        List<Employee> unmodifiable = Collections.unmodifiableList(employees);
        try {
            unmodifiable.add(null);
        } catch (UnsupportedOperationException e) {
            System.out.println("Unmodifiable list correctly rejects add()");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Migration Checklist — Java 7 → Java 8
    // -------------------------------------------------------------------------
    static void migrationChecklist() {
        System.out.println("--- 7. Java 7 → Java 8 Migration Checklist ---");

        System.out.println(
            "Language & API\n" +
            "  ✓ Anonymous inner class → lambda (where it implements a functional interface)\n" +
            "  ✓ Comparator boilerplate → Comparator.comparing().thenComparing()\n" +
            "  ✓ Null guard returns → Optional from service methods\n" +
            "  ✓ Loops that filter+transform+collect → Stream pipeline\n" +
            "  ✓ new Date() / Calendar → LocalDate / LocalDateTime / ZonedDateTime\n" +
            "  ✓ SimpleDateFormat (not thread-safe) → DateTimeFormatter (thread-safe)\n" +
            "  ✓ org.apache.commons.codec.binary.Base64 → java.util.Base64\n" +
            "\n" +
            "Collections & Maps\n" +
            "  ✓ containsKey() + put() pattern → computeIfAbsent()\n" +
            "  ✓ Manual frequency map loop → Map.merge(key, 1, Integer::sum)\n" +
            "  ✓ for-each remove → List.removeIf(predicate)\n" +
            "  ✓ map.get(key) with null-check → map.getOrDefault(key, default)\n" +
            "\n" +
            "Concurrency\n" +
            "  ✓ Future<T> + .get() blocking chain → CompletableFuture pipeline\n" +
            "  ✓ AtomicLong under high contention → LongAdder\n" +
            "  ✓ ReadWriteLock read-heavy → StampedLock with optimistic reads\n" +
            "\n" +
            "JVM & Build\n" +
            "  ✓ source/target 1.7 in pom.xml/build.gradle → 1.8\n" +
            "  ✓ -XX:MaxPermSize JVM flag → remove (PermGen gone, Metaspace auto-sizes)\n" +
            "  ✓ Add -XX:MetaspaceSize=256m if you had a small PermGen\n"
        );

        // Demonstrate several migration patterns in one place
        List<Employee> employees = Employee.SampleData.employees();

        // Before: null guard
        // if (findById(employees, 1L) != null) { ... }
        // After: Optional
        findTopEarner(employees, Department.ENGINEERING)
                .ifPresent(e -> System.out.println("Top engineer: " + e.getName()));

        // Before: Calendar for date arithmetic
        // Calendar cal = Calendar.getInstance(); cal.add(Calendar.MONTH, -3);
        // After: LocalDate
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        long veterans = employees.stream()
                .filter(e -> e.getJoinDate().isBefore(threeMonthsAgo))
                .count();
        System.out.println("Joined before " + threeMonthsAgo + ": " + veterans + " employees");

        // Before: Comparator anonymous inner class
        // Collections.sort(employees, new Comparator<Employee>() { ... });
        // After: method reference + thenComparing
        employees.stream()
                .sorted(Comparator.comparing(Employee::getDepartment)
                                  .thenComparing(Employee::getName))
                .limit(3)
                .forEach(e -> System.out.printf("  %-12s %s%n", e.getDepartment(), e.getName()));
    }

    // =========================================================================
    // Private helpers — extracted from lambdas for readability
    // =========================================================================

    static boolean isSeniorHighEarner(Employee e) {
        return e.isActive()
                && e.getSalary() > 90_000
                && e.getJoinDate().isBefore(LocalDate.of(2020, 1, 1));
    }

    static String formatEmployeeReport(Employee e) {
        long years = e.getJoinDate().until(LocalDate.now()).getYears();
        return String.format("%-15s £%.0f (%d yr)", e.getName(), e.getSalary(), years);
    }

    static String toPayslip(Employee e) {
        double net = e.getSalary() * 0.78;
        return String.format("Payslip: %-15s gross=£%.0f net=£%.0f", e.getName(), e.getSalary(), net);
    }

    static Optional<Employee> findTopEarner(List<Employee> employees, Department dept) {
        return employees.stream()
                .filter(e -> e.getDepartment() == dept && e.isActive())
                .max(Comparator.comparingDouble(Employee::getSalary));
    }

    static String fetchData() {
        return "data-from-service";
    }
}

package com.devopsmonk.java8.ch06_streams;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;

import java.util.*;
import java.util.stream.*;

/**
 * Chapter 6 — Streams API: Introduction
 * Tutorial: https://devops-monk.com/tutorials/java8/streams-introduction/
 *
 * Covers:
 *  - Creating streams (from collections, arrays, of(), range, iterate, generate)
 *  - Intermediate operations: filter, map, mapToInt, distinct, sorted, peek, limit, skip
 *  - Terminal operations: forEach, collect, count, sum, min, max, findFirst, anyMatch
 *  - Laziness: nothing runs until a terminal operation is called
 *  - Short-circuiting operations
 *  - Internal vs external iteration
 */
public class StreamBasicsExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 6: Streams API — Introduction ===\n");

        creatingStreams();
        intermediateOperations();
        terminalOperations();
        lazinessDemo();
        shortCircuiting();
        streamVsLoop();
    }

    // -------------------------------------------------------------------------
    // 1. Creating Streams
    // -------------------------------------------------------------------------
    static void creatingStreams() {
        System.out.println("--- 1. Creating Streams ---");

        // From a Collection
        List<Employee> employees = Employee.SampleData.employees();
        Stream<Employee> fromList = employees.stream();
        System.out.println("From list: " + fromList.count() + " employees");

        // From an array
        String[] languages = {"Java", "Kotlin", "Scala", "Groovy"};
        Stream<String> fromArray = Arrays.stream(languages);
        System.out.println("JVM languages: " + fromArray.collect(Collectors.joining(", ")));

        // Stream.of() — varargs
        Stream<Integer> ofStream = Stream.of(1, 2, 3, 4, 5);
        System.out.println("Sum of 1-5: " + ofStream.mapToInt(Integer::intValue).sum());

        // IntStream.range / rangeClosed
        System.out.print("Range 1-5: ");
        IntStream.rangeClosed(1, 5).forEach(n -> System.out.print(n + " "));
        System.out.println();

        // Stream.iterate — infinite stream, must limit()
        System.out.print("Powers of 2: ");
        Stream.iterate(1, n -> n * 2).limit(8).forEach(n -> System.out.print(n + " "));
        System.out.println();

        // Stream.generate — infinite, must limit()
        System.out.print("Random doubles: ");
        Stream.generate(Math::random).limit(4)
              .map(d -> String.format("%.3f", d))
              .forEach(s -> System.out.print(s + " "));
        System.out.println();

        // Primitive streams — avoid boxing/unboxing
        IntStream    intStream    = IntStream.of(1, 2, 3);
        LongStream   longStream   = LongStream.range(1L, 4L);
        DoubleStream doubleStream = DoubleStream.of(1.1, 2.2, 3.3);
        System.out.println("IntStream sum: " + intStream.sum());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Intermediate Operations — lazy, return a new Stream
    // -------------------------------------------------------------------------
    static void intermediateOperations() {
        System.out.println("--- 2. Intermediate Operations ---");

        List<Employee> employees = Employee.SampleData.employees();

        // filter — keep elements matching the predicate
        long activeCount = employees.stream()
                .filter(Employee::isActive)
                .count();
        System.out.println("Active employees: " + activeCount);

        // map — transform each element
        List<String> names = employees.stream()
                .map(Employee::getName)
                .collect(Collectors.toList());
        System.out.println("All names: " + names.size() + " entries");

        // mapToDouble / mapToInt — avoid boxing
        OptionalDouble avgSalary = employees.stream()
                .filter(Employee::isActive)
                .mapToDouble(Employee::getSalary)
                .average();
        avgSalary.ifPresent(avg -> System.out.printf("Avg active salary: £%.0f%n", avg));

        // distinct — remove duplicates (uses equals)
        List<Department> uniqueDepts = employees.stream()
                .map(Employee::getDepartment)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        System.out.println("Departments: " + uniqueDepts);

        // sorted — natural order or with Comparator
        employees.stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .limit(3)
                .forEach(e -> System.out.printf("  %-15s £%.0f%n", e.getName(), e.getSalary()));

        // peek — for debugging; doesn't change the stream
        long count = employees.stream()
                .filter(Employee::isActive)
                .peek(e -> System.out.print("  [checking " + e.getName() + "]"))
                .filter(e -> e.getSalary() > 90_000)
                .peek(e -> System.out.print(" [PASSED]"))
                .count();
        System.out.println("\nPassed filter: " + count);

        // limit and skip — pagination
        System.out.println("Page 2 (skip 5, take 5):");
        employees.stream()
                .skip(5)
                .limit(5)
                .forEach(e -> System.out.println("  " + e.getName()));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Terminal Operations — eager, trigger the pipeline
    // -------------------------------------------------------------------------
    static void terminalOperations() {
        System.out.println("--- 3. Terminal Operations ---");

        List<Employee> employees = Employee.SampleData.employees();

        // forEach
        System.out.print("forEach: ");
        employees.stream().limit(3).map(Employee::getName).forEach(n -> System.out.print(n + " "));
        System.out.println();

        // collect
        List<String> engineerNames = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .map(Employee::getName)
                .collect(Collectors.toList());
        System.out.println("Engineers: " + engineerNames);

        // count
        long engineers = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .count();
        System.out.println("Engineer count: " + engineers);

        // sum / average / min / max on numeric streams
        double totalPayroll = employees.stream()
                .mapToDouble(Employee::getSalary)
                .sum();
        System.out.printf("Total payroll: £%.0f%n", totalPayroll);

        // min / max with Comparator
        Optional<Employee> lowestPaid = employees.stream()
                .min(Comparator.comparingDouble(Employee::getSalary));
        lowestPaid.ifPresent(e -> System.out.println("Lowest paid: " + e.getName() + " £" + (int) e.getSalary()));

        // findFirst / findAny (findAny is for parallel streams)
        Optional<Employee> firstEngineer = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .findFirst();
        firstEngineer.ifPresent(e -> System.out.println("First engineer: " + e.getName()));

        // anyMatch / allMatch / noneMatch — short-circuit terminal ops
        boolean hasHighEarner  = employees.stream().anyMatch(e -> e.getSalary() > 110_000);
        boolean allActive      = employees.stream().allMatch(Employee::isActive);
        boolean noneNegSalary  = employees.stream().noneMatch(e -> e.getSalary() < 0);

        System.out.println("Any earning >110k: " + hasHighEarner);
        System.out.println("All active:        " + allActive);
        System.out.println("None negative:     " + noneNegSalary);

        // reduce — fold a stream to a single value
        double maxSalary = employees.stream()
                .mapToDouble(Employee::getSalary)
                .reduce(0.0, Double::max);
        System.out.printf("Max salary via reduce: £%.0f%n", maxSalary);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Laziness — intermediate ops don't execute until terminal op fires
    // -------------------------------------------------------------------------
    static void lazinessDemo() {
        System.out.println("--- 4. Laziness Demo ---");

        System.out.println("Building the pipeline (nothing printed yet)...");

        Stream<Employee> pipeline = Employee.SampleData.employees().stream()
                .filter(e -> {
                    System.out.println("  filter called for: " + e.getName());
                    return e.getDepartment() == Department.ENGINEERING;
                })
                .map(e -> {
                    System.out.println("  map called for: " + e.getName());
                    return e;
                });

        System.out.println("Pipeline built. Calling findFirst() now...");
        pipeline.findFirst().ifPresent(e -> System.out.println("Found: " + e.getName()));

        System.out.println("Notice: only processed until the first match — that's laziness + short-circuit.");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Short-Circuiting — stop processing as soon as result is known
    // -------------------------------------------------------------------------
    static void shortCircuiting() {
        System.out.println("--- 5. Short-Circuiting Operations ---");

        List<Integer> numbers = IntStream.rangeClosed(1, 1_000_000)
                .boxed().collect(Collectors.toList());

        // limit — take the first N, stop immediately
        long start = System.nanoTime();
        numbers.stream().filter(n -> n % 2 == 0).limit(5).forEach(n -> {});
        long elapsed = System.nanoTime() - start;
        System.out.printf("limit(5) from 1M: %,d ns%n", elapsed);

        // findFirst with filter — short-circuits on first match
        Optional<Integer> firstBig = numbers.stream()
                .filter(n -> n > 500_000)
                .findFirst();
        System.out.println("First number > 500,000: " + firstBig.orElse(-1));

        // anyMatch — stops at first true
        boolean hasMillionth = numbers.stream().anyMatch(n -> n == 999_999);
        System.out.println("Contains 999,999: " + hasMillionth);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Stream vs For-Loop — when each is appropriate
    // -------------------------------------------------------------------------
    static void streamVsLoop() {
        System.out.println("--- 6. Stream vs For-Loop ---");

        List<Employee> employees = Employee.SampleData.employees();

        // For-loop version (easier to debug, fine for simple cases)
        double totalLoop = 0;
        for (Employee e : employees) {
            if (e.isActive()) totalLoop += e.getSalary();
        }
        System.out.printf("Total (loop):   £%.0f%n", totalLoop);

        // Stream version (composable, parallelisable, no mutation)
        double totalStream = employees.stream()
                .filter(Employee::isActive)
                .mapToDouble(Employee::getSalary)
                .sum();
        System.out.printf("Total (stream): £%.0f%n", totalStream);

        // Use streams when:
        // - pipeline has 2+ transformation steps
        // - you need parallel processing
        // - you want to compose with Optional or other streams
        // Use loops when:
        // - you need to break/continue mid-iteration (streams can't break)
        // - you're mutating external state per element
        // - the loop is so simple that stream adds noise, not clarity
        System.out.println("\nBoth produce the same result. Use whichever is clearer for the reader.");
    }
}

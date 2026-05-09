package com.devopsmonk.java8.ch05_methodrefs;

import com.devopsmonk.java8.model.Employee;
import com.devopsmonk.java8.model.Product;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * Chapter 5 — Method References
 * Tutorial: https://devops-monk.com/tutorials/java8/method-references/
 *
 * The four kinds:
 *  1. Static method reference:          ClassName::staticMethod
 *  2. Bound instance method reference:  instance::instanceMethod
 *  3. Unbound instance method reference: ClassName::instanceMethod
 *  4. Constructor reference:            ClassName::new
 */
public class MethodReferenceExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 5: Method References ===\n");

        staticMethodReferences();
        boundInstanceReferences();
        unboundInstanceReferences();
        constructorReferences();
        methodRefsInStreams();
        whenToUseLambdaInstead();
    }

    // -------------------------------------------------------------------------
    // 1. Static Method Reference — ClassName::staticMethod
    //    Equivalent to lambda: (args) -> ClassName.staticMethod(args)
    // -------------------------------------------------------------------------
    static void staticMethodReferences() {
        System.out.println("--- 1. Static Method References ---");

        // Integer::parseInt — same as s -> Integer.parseInt(s)
        Function<String, Integer> parse = Integer::parseInt;
        System.out.println("Parsed: " + parse.apply("42"));

        // Integer::compare — same as (a, b) -> Integer.compare(a, b)
        Comparator<Integer> compareInts = Integer::compare;
        List<Integer> nums = Arrays.asList(5, 2, 8, 1, 9, 3);
        nums.sort(compareInts);
        System.out.println("Sorted: " + nums);

        // Math::abs — same as n -> Math.abs(n)
        Function<Integer, Integer> abs = Math::abs;
        System.out.println("Abs of -17: " + abs.apply(-17));

        // Our own static helper
        List<String> salaryStrings = Arrays.asList("95000", "88000", "82000");
        salaryStrings.stream()
                     .map(Integer::parseInt)           // static ref
                     .map(MethodReferenceExamples::formatSalary)  // our static ref
                     .forEach(System.out::println);   // PrintStream instance ref

        System.out.println();
    }

    static String formatSalary(int salary) {
        return String.format("  £%,d", salary);
    }

    // -------------------------------------------------------------------------
    // 2. Bound Instance Method Reference — instance::method
    //    Equivalent to lambda: (args) -> instance.method(args)
    //    The receiver object is fixed (bound) at reference creation time.
    // -------------------------------------------------------------------------
    static void boundInstanceReferences() {
        System.out.println("--- 2. Bound Instance Method References ---");

        String prefix = "EMP-";

        // prefix::concat — bound to the specific String instance 'prefix'
        Function<String, String> addPrefix = prefix::concat;
        System.out.println(addPrefix.apply("001"));
        System.out.println(addPrefix.apply("002"));

        // System.out is an instance — System.out::println is a bound ref
        Consumer<String> print = System.out::println;
        Arrays.asList("one", "two", "three").forEach(print);

        // A service object's method
        SalaryCalculator calc = new SalaryCalculator(1.10);
        Function<Double, Double> applyRaise = calc::applyRaise;  // bound to 'calc'

        Employee.SampleData.employees().stream()
                .limit(3)
                .forEach(e -> System.out.printf("  %-15s  was=£%.0f  now=£%.0f%n",
                        e.getName(), e.getSalary(), applyRaise.apply(e.getSalary())));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Unbound Instance Method Reference — ClassName::instanceMethod
    //    Equivalent to lambda: (instance, args) -> instance.method(args)
    //    The receiver is supplied by the stream or calling code — not fixed.
    // -------------------------------------------------------------------------
    static void unboundInstanceReferences() {
        System.out.println("--- 3. Unbound Instance Method References ---");

        // String::toUpperCase — same as s -> s.toUpperCase()
        // The String instance is the first argument supplied by the stream
        Function<String, String> upper = String::toUpperCase;
        System.out.println(upper.apply("hello"));

        // String::length — same as s -> s.length()
        Function<String, Integer> len = String::length;
        List<String> words = Arrays.asList("Lambda", "Stream", "Optional", "CompletableFuture");
        words.stream()
             .sorted(Comparator.comparingInt(String::length))  // unbound ref in comparator
             .forEach(w -> System.out.printf("  %-20s (%d chars)%n", w, len.apply(w)));

        // Employee::getName — unbound, the Employee is the receiver
        List<String> names = Employee.SampleData.employees().stream()
                .map(Employee::getName)          // unbound — Employee is the stream element
                .sorted()
                .collect(Collectors.toList());
        System.out.println("Names: " + names);

        // Comparator using unbound refs
        Employee.SampleData.employees().stream()
                .sorted(Comparator.comparing(Employee::getDepartment)
                                  .thenComparing(Employee::getName))
                .limit(5)
                .forEach(e -> System.out.printf("  %-12s  %s%n", e.getDepartment(), e.getName()));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Constructor Reference — ClassName::new
    //    Equivalent to lambda: (args) -> new ClassName(args)
    // -------------------------------------------------------------------------
    static void constructorReferences() {
        System.out.println("--- 4. Constructor References ---");

        // String::new — same as s -> new String(s)
        Function<String, String> newString = String::new;
        System.out.println(newString.apply("hello"));

        // ArrayList::new — same as () -> new ArrayList<>()
        Supplier<List<String>> newList = ArrayList::new;
        List<String> list = newList.get();
        list.add("item");
        System.out.println("Created list: " + list);

        // Constructor with one arg — Function<T, Product>
        // Imagine a simple factory: product names → Product objects with defaults
        List<String> productNames = Arrays.asList("Laptop", "Phone", "Tablet");
        // Using a factory method instead (Product constructor takes more args)
        Function<String, SimpleItem> itemFactory = SimpleItem::new;
        productNames.stream()
                    .map(itemFactory)
                    .forEach(System.out::println);

        // BiFunction for two-arg constructor
        BiFunction<String, Double, SimpleItem> pricedItemFactory = SimpleItem::new;
        SimpleItem item = pricedItemFactory.apply("Monitor", 399.99);
        System.out.println("Created: " + item);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Method References in Stream Pipelines
    // -------------------------------------------------------------------------
    static void methodRefsInStreams() {
        System.out.println("--- 5. Method References in Stream Pipelines ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Chaining method references in a pipeline
        double avgSalary = employees.stream()
                .filter(Employee::isActive)           // unbound — Predicate<Employee>
                .mapToDouble(Employee::getSalary)     // unbound — ToDoubleFunction<Employee>
                .average()
                .orElse(0.0);
        System.out.printf("Average active salary: £%.2f%n", avgSalary);

        // Collecting with method references
        Map<String, Double> salaryByName = employees.stream()
                .collect(Collectors.toMap(Employee::getName, Employee::getSalary));
        System.out.println("Alice's salary: £" + (int) salaryByName.get("Alice Chen"));

        // Sorting with chained method reference comparators
        employees.stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .limit(3)
                .map(e -> e.getName() + " (£" + (int) e.getSalary() + ")")
                .forEach(s -> System.out.println("  " + s));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. When to Keep a Lambda Instead
    //    Method refs improve readability — but not always. Know when to stop.
    // -------------------------------------------------------------------------
    static void whenToUseLambdaInstead() {
        System.out.println("--- 6. When to Keep a Lambda ---");

        // GOOD — method ref is cleaner
        Employee.SampleData.employees().stream()
                .map(Employee::getName)
                .forEach(System.out::println);

        // BAD — method ref obscures the intent
        // Avoid:   .filter(Objects::nonNull)  when you have   .filter(e -> e != null)
        // Prefer a lambda when the method ref requires a cast or wrapping:

        // This would require a helper method or cast — lambda is clearer:
        Employee.SampleData.employees().stream()
                .filter(e -> e.getSalary() > 90_000)     // lambda — condition is obvious
                .map(e -> e.getName().toUpperCase())      // lambda — two operations chained
                .forEach(System.out::println);

        // Method ref hurts readability when the argument order doesn't match naturally:
        // e.g. String::valueOf used where it isn't obvious which overload is chosen

        System.out.println("\nRule of thumb: use method refs when they read like plain English.");
        System.out.println("Use lambdas when the operation has logic that needs to be visible.");
    }

    // -------------------------------------------------------------------------
    // Helper classes
    // -------------------------------------------------------------------------

    static class SalaryCalculator {
        private final double multiplier;
        SalaryCalculator(double multiplier) { this.multiplier = multiplier; }
        double applyRaise(double salary) { return salary * multiplier; }
    }

    static class SimpleItem {
        final String name;
        final double price;
        SimpleItem(String name) { this.name = name; this.price = 0.0; }
        SimpleItem(String name, double price) { this.name = name; this.price = price; }
        @Override public String toString() {
            return String.format("SimpleItem{name='%s', price=%.2f}", name, price);
        }
    }
}

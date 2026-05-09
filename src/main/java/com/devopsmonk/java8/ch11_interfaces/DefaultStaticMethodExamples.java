package com.devopsmonk.java8.ch11_interfaces;

import com.devopsmonk.java8.model.Employee;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chapter 11 — Default and Static Interface Methods
 * Tutorial: https://devops-monk.com/tutorials/java8/default-static-methods/
 *
 * Covers:
 *  - Default methods: purpose, syntax, diamond problem resolution
 *  - Static methods in interfaces
 *  - Comparator API built on default/static methods
 *  - Collection.forEach, removeIf, replaceAll
 *  - Building a pluggable discount strategy with default methods
 */
public class DefaultStaticMethodExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 11: Default and Static Interface Methods ===\n");

        defaultMethodBasics();
        diamondProblem();
        staticMethodsInInterfaces();
        comparatorApi();
        collectionDefaultMethods();
        discountStrategyExample();
    }

    // -------------------------------------------------------------------------
    // 1. Default Methods — why they exist and how they work
    // -------------------------------------------------------------------------
    static void defaultMethodBasics() {
        System.out.println("--- 1. Default Method Basics ---");

        // Without default methods, adding sort() to List in Java 8 would have
        // broken every class that implemented List (thousands of them).
        // Default methods let interfaces evolve without breaking implementors.

        Greeter english = new EnglishGreeter();
        Greeter french  = new FrenchGreeter();
        Greeter custom  = new CustomGreeter();

        // All three use the default greetAll — only CustomGreeter overrides it
        System.out.println(english.greet("Alice"));
        System.out.println(french.greet("Alice"));

        english.greetAll(Arrays.asList("Bob", "Carol", "Dave"));
        custom.greetAll(Arrays.asList("Bob", "Carol", "Dave"));  // uses override

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Diamond Problem — multiple interfaces with same default method
    // -------------------------------------------------------------------------
    static void diamondProblem() {
        System.out.println("--- 2. Diamond Problem Resolution ---");

        // Rule 1: A class method always wins over an interface default.
        // Rule 2: A more specific interface wins over a less specific one.
        // Rule 3: If still ambiguous, the class must explicitly override.

        DiamondChild child = new DiamondChild();
        System.out.println(child.hello()); // DiamondChild explicitly resolves the conflict

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Static Methods in Interfaces — factory and utility methods
    // -------------------------------------------------------------------------
    static void staticMethodsInInterfaces() {
        System.out.println("--- 3. Static Methods in Interfaces ---");

        // Static interface methods replace utility classes like Collections or Lists
        // They cannot be inherited or overridden by implementing classes

        Validator<String> notEmpty    = Validator.notEmpty();
        Validator<String> maxLen20    = Validator.maxLength(20);
        Validator<String> combined    = notEmpty.and(maxLen20);

        System.out.println("notEmpty(''):      " + notEmpty.validate(""));
        System.out.println("notEmpty('hello'): " + notEmpty.validate("hello"));
        System.out.println("maxLen('this string is way too long for the validator'): "
                + maxLen20.validate("this string is way too long for the validator"));
        System.out.println("combined('Alice'): " + combined.validate("Alice"));

        // Comparable to Comparator.comparing — a static factory on the interface
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Comparator API — built entirely on default and static methods
    // -------------------------------------------------------------------------
    static void comparatorApi() {
        System.out.println("--- 4. Comparator API (default + static methods) ---");

        List<Employee> employees = new ArrayList<>(Employee.SampleData.employees());

        // Comparator.comparing — static factory method on the Comparator interface
        Comparator<Employee> bySalary = Comparator.comparingDouble(Employee::getSalary);
        Comparator<Employee> byName   = Comparator.comparing(Employee::getName);
        Comparator<Employee> byDept   = Comparator.comparing(Employee::getDepartment);

        // reversed() — default method on Comparator
        Comparator<Employee> bySalaryDesc = bySalary.reversed();

        // thenComparing — default method for secondary sort key
        Comparator<Employee> byDeptThenSalaryDesc = byDept.thenComparing(bySalaryDesc);

        employees.sort(byDeptThenSalaryDesc);
        System.out.println("Sorted by dept, then salary desc:");
        employees.stream().limit(6).forEach(e ->
                System.out.printf("  %-12s %-15s £%.0f%n", e.getDepartment(), e.getName(), e.getSalary()));

        // Comparator.nullsFirst / nullsLast — handle null values safely
        List<String> withNulls = Arrays.asList("Banana", null, "Apple", null, "Cherry");
        withNulls.sort(Comparator.nullsFirst(String::compareTo));
        System.out.println("\nNull-safe sort (nulls first): " + withNulls);

        // Comparator.naturalOrder / reverseOrder
        List<Integer> nums = Arrays.asList(5, 2, 8, 1, 9, 3);
        nums.sort(Comparator.reverseOrder());
        System.out.println("Reverse order: " + nums);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Collection Default Methods added in Java 8
    // -------------------------------------------------------------------------
    static void collectionDefaultMethods() {
        System.out.println("--- 5. Collection Default Methods ---");

        List<String> names = new ArrayList<>(Arrays.asList("Alice", "Bob", "", "Carol", "  ", "Dave"));

        // forEach — default on Iterable
        System.out.print("forEach: ");
        names.forEach(n -> System.out.print("[" + n + "] "));
        System.out.println();

        // removeIf — default on Collection, removes elements matching predicate
        names.removeIf(s -> s.isBlank());
        System.out.println("After removeIf(blank): " + names);

        // replaceAll — default on List, applies UnaryOperator to each element in place
        names.replaceAll(String::toUpperCase);
        System.out.println("After replaceAll(upper): " + names);

        // sort on List (calls Arrays.sort internally)
        names.sort(Comparator.naturalOrder());
        System.out.println("After sort: " + names);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Pluggable Discount Strategy — default methods as extension points
    // -------------------------------------------------------------------------
    static void discountStrategyExample() {
        System.out.println("--- 6. Discount Strategy with Default Methods ---");

        // DiscountStrategy is an interface with a default method for composition
        DiscountStrategy noDiscount     = DiscountStrategy.none();
        DiscountStrategy tenPercent     = DiscountStrategy.percentage(10);
        DiscountStrategy fiftyFlat      = DiscountStrategy.flatOff(50);
        DiscountStrategy bulkAndPercent = fiftyFlat.andThen(tenPercent);

        double price = 500.0;
        System.out.printf("Original:          £%.2f%n", price);
        System.out.printf("No discount:       £%.2f%n", noDiscount.apply(price));
        System.out.printf("10%% off:           £%.2f%n", tenPercent.apply(price));
        System.out.printf("£50 flat off:      £%.2f%n", fiftyFlat.apply(price));
        System.out.printf("£50 then 10%% off:  £%.2f%n", bulkAndPercent.apply(price));
    }

    // =========================================================================
    // Supporting interfaces and classes
    // =========================================================================

    // --- Greeter ---

    interface Greeter {
        String greet(String name);

        // Default method — implementing classes get this for free
        default void greetAll(List<String> names) {
            System.out.println("  (default greetAll)");
            names.forEach(n -> System.out.println("  " + greet(n)));
        }
    }

    static class EnglishGreeter implements Greeter {
        @Override public String greet(String name) { return "Hello, " + name + "!"; }
    }

    static class FrenchGreeter implements Greeter {
        @Override public String greet(String name) { return "Bonjour, " + name + "!"; }
    }

    static class CustomGreeter implements Greeter {
        @Override public String greet(String name) { return "Hey " + name + "!"; }

        @Override
        public void greetAll(List<String> names) {
            System.out.println("  (custom greetAll — all at once)");
            System.out.println("  " + names.stream().map(this::greet).collect(Collectors.joining(", ")));
        }
    }

    // --- Diamond Problem ---

    interface A { default String hello() { return "Hello from A"; } }
    interface B extends A { @Override default String hello() { return "Hello from B"; } }
    interface C extends A { @Override default String hello() { return "Hello from C"; } }

    static class DiamondChild implements B, C {
        // Both B and C provide hello() — must resolve explicitly
        @Override
        public String hello() {
            return B.super.hello() + " (DiamondChild chose B)";
        }
    }

    // --- Validator ---

    @FunctionalInterface
    interface Validator<T> {
        boolean validate(T value);

        default Validator<T> and(Validator<T> other) {
            return value -> this.validate(value) && other.validate(value);
        }

        default Validator<T> or(Validator<T> other) {
            return value -> this.validate(value) || other.validate(value);
        }

        static Validator<String> notEmpty() {
            return s -> s != null && !s.isBlank();
        }

        static Validator<String> maxLength(int max) {
            return s -> s != null && s.length() <= max;
        }
    }

    // --- DiscountStrategy ---

    @FunctionalInterface
    interface DiscountStrategy {
        double apply(double price);

        default DiscountStrategy andThen(DiscountStrategy next) {
            return price -> next.apply(this.apply(price));
        }

        static DiscountStrategy none() {
            return price -> price;
        }

        static DiscountStrategy percentage(double pct) {
            return price -> price * (1 - pct / 100);
        }

        static DiscountStrategy flatOff(double amount) {
            return price -> Math.max(0, price - amount);
        }
    }
}

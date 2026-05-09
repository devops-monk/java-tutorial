package com.devopsmonk.java8.ch03_lambdas;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Chapter 3 — Lambda Expressions
 * Tutorial: https://devops-monk.com/tutorials/java8/lambdas/
 *
 * Covers:
 *  - Lambda syntax (no params, one param, multiple params, block body)
 *  - Replacing anonymous inner classes
 *  - Target typing and type inference
 *  - Effectively final and variable capture (closures)
 *  - Lambda as Comparator
 *  - Lambdas in real sorting and filtering scenarios
 */
public class LambdaExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 3: Lambda Expressions ===\n");

        anonymousInnerClassVsLambda();
        lambdaSyntaxVariants();
        targetTyping();
        closuresAndEffectivelyFinal();
        sortingWithLambdas();
        lambdasInPractice();
    }

    // -------------------------------------------------------------------------
    // 1. Before vs After: Anonymous Inner Class → Lambda
    // -------------------------------------------------------------------------
    static void anonymousInnerClassVsLambda() {
        System.out.println("--- 1. Anonymous Inner Class vs Lambda ---");

        List<String> names = Arrays.asList("Charlie", "Alice", "Bob", "David");

        // Java 7 style — boilerplate drowns the intent
        Collections.sort(names, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareTo(b);
            }
        });
        System.out.println("Sorted (Java 7 style): " + names);

        // Java 8 lambda — intent is immediately clear
        names.sort((a, b) -> a.compareTo(b));
        System.out.println("Sorted (Lambda):       " + names);

        // Even simpler: method reference
        names.sort(String::compareTo);
        System.out.println("Sorted (Method ref):   " + names);

        // Thread: anonymous inner class vs lambda
        Runnable oldStyle = new Runnable() {
            @Override
            public void run() {
                System.out.println("Running on thread (old style)");
            }
        };

        Runnable lambda = () -> System.out.println("Running on thread (lambda)");

        new Thread(oldStyle).start();
        new Thread(lambda).start();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Lambda Syntax Variants
    // -------------------------------------------------------------------------
    static void lambdaSyntaxVariants() {
        System.out.println("--- 2. Lambda Syntax Variants ---");

        // No parameters
        Runnable noParams = () -> System.out.println("No parameters");
        noParams.run();

        // One parameter — parentheses optional
        Function<String, Integer> oneParam = s -> s.length();
        System.out.println("Length of 'hello': " + oneParam.apply("hello"));

        // One parameter with explicit type
        Function<String, String> withType = (String s) -> s.toUpperCase();
        System.out.println("Uppercased: " + withType.apply("java"));

        // Two parameters
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
        System.out.println("3 + 4 = " + add.apply(3, 4));

        // Block body (multiple statements)
        BiFunction<String, Integer, String> repeat = (str, times) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(str);
            }
            return sb.toString();
        };
        System.out.println("Repeated: " + repeat.apply("Java", 3));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Target Typing — same lambda, different functional interfaces
    // -------------------------------------------------------------------------
    static void targetTyping() {
        System.out.println("--- 3. Target Typing ---");

        // The lambda `s -> s.isEmpty()` can target different functional interfaces
        // depending on what the compiler expects

        Predicate<String> isEmptyPred  = s -> s.isEmpty();
        Function<String, Boolean> isEmptyFn = s -> s.isEmpty();

        System.out.println("Predicate result:  " + isEmptyPred.test(""));
        System.out.println("Function result:   " + isEmptyFn.apply(""));
        System.out.println("Predicate result:  " + isEmptyPred.test("hello"));

        // Comparator<String> — the compiler infers both parameter types
        Comparator<String> byLength = (a, b) -> Integer.compare(a.length(), b.length());
        List<String> words = Arrays.asList("banana", "fig", "apple", "kiwi");
        words.sort(byLength);
        System.out.println("Sorted by length: " + words);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Closures — capturing variables from enclosing scope
    // -------------------------------------------------------------------------
    static void closuresAndEffectivelyFinal() {
        System.out.println("--- 4. Closures and Effectively Final ---");

        String greeting = "Hello";             // effectively final — never reassigned
        int    multiplier = 3;                 // effectively final

        // Lambda captures 'greeting' and 'multiplier' from the enclosing scope
        Function<String, String> greet = name -> greeting + ", " + name + "!";
        Function<Integer, Integer> triple = n -> n * multiplier;

        System.out.println(greet.apply("Alice"));
        System.out.println("Triple 7: " + triple.apply(7));

        // Simulating a per-request rate limiter using closure over a threshold
        double threshold = 1000.0;
        Predicate<Employee> isHighEarner = emp -> emp.getSalary() > threshold;

        List<Employee> employees = Employee.SampleData.employees();
        System.out.print("High earners (salary > " + threshold + "): ");
        employees.stream()
                 .filter(isHighEarner)
                 .map(Employee::getName)
                 .forEach(n -> System.out.print(n + "  "));
        System.out.println();

        // THIS WOULD NOT COMPILE — you cannot mutate a captured variable:
        // int counter = 0;
        // Runnable r = () -> counter++;   // ERROR: Variable used in lambda should be effectively final

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Sorting with Lambdas — multiple criteria
    // -------------------------------------------------------------------------
    static void sortingWithLambdas() {
        System.out.println("--- 5. Sorting with Lambdas ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Sort by salary descending
        employees.sort((e1, e2) -> Double.compare(e2.getSalary(), e1.getSalary()));
        System.out.println("Top 3 by salary (desc):");
        employees.stream().limit(3).forEach(e ->
                System.out.printf("  %-15s  $%.0f%n", e.getName(), e.getSalary()));

        // Sort by department, then name
        employees.sort((e1, e2) -> {
            int deptCompare = e1.getDepartment().compareTo(e2.getDepartment());
            if (deptCompare != 0) return deptCompare;
            return e1.getName().compareTo(e2.getName());
        });
        System.out.println("\nFirst 5 sorted by dept then name:");
        employees.stream().limit(5).forEach(e ->
                System.out.printf("  %-12s  %-15s%n", e.getDepartment(), e.getName()));

        // Using Comparator.comparing (cleaner — chapter 11 covers this in depth)
        employees.sort(Comparator.comparing(Employee::getDepartment)
                                 .thenComparing(Employee::getName));
        System.out.println("\nUsing Comparator.comparing: same result, more readable");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Lambdas in Practice — payroll rule engine
    // -------------------------------------------------------------------------
    static void lambdasInPractice() {
        System.out.println("--- 6. Lambdas in Practice: Payroll Rule Engine ---");

        // Store business rules as lambdas in variables
        Predicate<Employee> isEngineer   = e -> e.getDepartment() == Department.ENGINEERING;
        Predicate<Employee> isHighEarner = e -> e.getSalary() > 90000;
        Predicate<Employee> isActive     = Employee::isActive;

        // Compose predicates — active engineers earning more than 90k
        Predicate<Employee> seniorEngineer = isActive.and(isEngineer).and(isHighEarner);

        // Apply a 10% bonus as a function
        Function<Employee, String> bonusReport = e ->
                String.format("%s: salary=%.0f, bonus=%.0f",
                        e.getName(), e.getSalary(), e.getSalary() * 0.10);

        System.out.println("Senior engineers eligible for 10% bonus:");
        Employee.SampleData.employees().stream()
                .filter(seniorEngineer)
                .map(bonusReport)
                .forEach(s -> System.out.println("  " + s));

        System.out.println();
    }
}

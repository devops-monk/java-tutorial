package com.devopsmonk.java8.ch09_optional;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Chapter 9 — Optional: Eliminating NullPointerException the Right Way
 * Tutorial: https://devops-monk.com/tutorials/java8/optional/
 *
 * Covers:
 *  - Creating Optional: of, ofNullable, empty
 *  - Consuming: get, isPresent, ifPresent
 *  - Transforming: map, flatMap, filter
 *  - Recovering: orElse, orElseGet, orElseThrow
 *  - Common anti-patterns to avoid
 *  - Optional in streams
 */
public class OptionalExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 9: Optional ===\n");

        creatingOptional();
        consumingOptional();
        transformingOptional();
        recoveringFromEmpty();
        optionalInStreams();
        antiPatterns();
        realWorldUsage();
    }

    // -------------------------------------------------------------------------
    // 1. Creating Optional
    // -------------------------------------------------------------------------
    static void creatingOptional() {
        System.out.println("--- 1. Creating Optional ---");

        // Optional.of() — value must be non-null, throws NPE if null
        Optional<String> present = Optional.of("Hello Java 8");
        System.out.println("of():         " + present);

        // Optional.ofNullable() — null-safe, wraps null as empty
        String maybeNull = null;
        Optional<String> fromNull   = Optional.ofNullable(maybeNull);
        Optional<String> fromValue  = Optional.ofNullable("value");
        System.out.println("ofNullable(null):  " + fromNull);
        System.out.println("ofNullable(value): " + fromValue);

        // Optional.empty() — an empty Optional
        Optional<Employee> noEmployee = Optional.empty();
        System.out.println("empty():      " + noEmployee);
        System.out.println("isPresent:    " + noEmployee.isPresent());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Consuming — check presence before using
    // -------------------------------------------------------------------------
    static void consumingOptional() {
        System.out.println("--- 2. Consuming Optional ---");

        List<Employee> employees = Employee.SampleData.employees();

        Optional<Employee> found = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .findFirst();

        // isPresent + get — verbose, avoid this pattern
        if (found.isPresent()) {
            System.out.println("Found (isPresent + get): " + found.get().getName());
        }

        // ifPresent — cleaner, no separate isPresent check
        found.ifPresent(e -> System.out.println("Found (ifPresent):       " + e.getName()));

        // isEmpty() — Java 11+, but good to know
        Optional<Employee> notFound = employees.stream()
                .filter(e -> e.getName().equals("Nobody"))
                .findFirst();
        System.out.println("Not found isPresent: " + notFound.isPresent());

        // get() on empty throws NoSuchElementException — never call without isPresent check
        try {
            notFound.get();
        } catch (NoSuchElementException e) {
            System.out.println("get() on empty: NoSuchElementException (as expected)");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Transforming — map, flatMap, filter
    // -------------------------------------------------------------------------
    static void transformingOptional() {
        System.out.println("--- 3. Transforming Optional ---");

        List<Employee> employees = Employee.SampleData.employees();

        // map — transform the value inside Optional (if present)
        Optional<String> engineerName = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .findFirst()
                .map(Employee::getName);               // Optional<Employee> → Optional<String>
        System.out.println("First engineer name: " + engineerName.orElse("none"));

        // map chain — transform multiple times
        Optional<Integer> nameLength = employees.stream()
                .filter(e -> e.getSalary() > 100_000)
                .findFirst()
                .map(Employee::getName)
                .map(String::length);
        System.out.println("Name length of 100k+ earner: " + nameLength.orElse(0));

        // filter — keep the value only if it passes the predicate
        Optional<Employee> highEarnerEngineer = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .findFirst()
                .filter(e -> e.getSalary() > 100_000);
        System.out.println("High-earning engineer: " +
                highEarnerEngineer.map(Employee::getName).orElse("none"));

        // flatMap — when the mapper itself returns Optional (avoids Optional<Optional<T>>)
        // Imagine a user profile that may or may not have an address
        Optional<String> address = findEmployeeById(employees, 1L)
                .flatMap(OptionalExamples::getManagerEmail);  // returns Optional<String>
        System.out.println("Manager email: " + address.orElse("no email"));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Recovering — provide defaults when empty
    // -------------------------------------------------------------------------
    static void recoveringFromEmpty() {
        System.out.println("--- 4. Recovering from Empty Optional ---");

        List<Employee> employees = Employee.SampleData.employees();

        Optional<Employee> notFound = employees.stream()
                .filter(e -> e.getName().equals("NoOne"))
                .findFirst();

        // orElse — always evaluates the default, even if not needed
        Employee defaultEmp = new Employee(0, "Default", Department.HR, 0, java.time.LocalDate.now());
        Employee result1 = notFound.orElse(defaultEmp);
        System.out.println("orElse:        " + result1.getName());

        // orElseGet — lazy: Supplier only called when Optional is empty
        // Prefer this when the default value is expensive to compute
        Employee result2 = notFound.orElseGet(() -> {
            System.out.println("  (computing lazy default)");
            return defaultEmp;
        });
        System.out.println("orElseGet:     " + result2.getName());

        // orElse vs orElseGet — the key difference:
        Optional<String> presentOpt = Optional.of("value");
        presentOpt.orElse(expensiveComputation("orElse"));      // called even though present!
        presentOpt.orElseGet(() -> expensiveComputation("orElseGet")); // NOT called

        // orElseThrow — throw an exception if empty
        try {
            Employee result3 = notFound.orElseThrow(
                    () -> new IllegalStateException("Employee not found in the system"));
        } catch (IllegalStateException e) {
            System.out.println("orElseThrow:   " + e.getMessage());
        }

        // Chaining: find an active engineer, or fall back to any active employee
        Employee assignee = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING && e.isActive())
                .findFirst()
                .orElseGet(() -> employees.stream()
                        .filter(Employee::isActive)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No active employees")));
        System.out.println("Assignee: " + assignee.getName());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Optional in Streams — filter out empty, unwrap, flatMap
    // -------------------------------------------------------------------------
    static void optionalInStreams() {
        System.out.println("--- 5. Optional in Streams ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Stream<Optional<String>> → Stream<String> (filter + map pattern)
        // Common when you have a stream of IDs and look up each one
        List<Long> ids = java.util.Arrays.asList(1L, 99L, 2L, 100L, 3L);

        List<String> foundNames = ids.stream()
                .map(id -> findEmployeeById(employees, id))  // Stream<Optional<Employee>>
                .filter(Optional::isPresent)                   // remove empty
                .map(opt -> opt.get().getName())               // unwrap
                .collect(Collectors.toList());
        System.out.println("Found names for IDs " + ids + ": " + foundNames);

        // Java 9+ has Optional.stream() which makes this cleaner:
        // ids.stream().flatMap(id -> findEmployeeById(employees, id).stream())
        // In Java 8, use the filter+map pattern above.

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Anti-Patterns — what NOT to do with Optional
    // -------------------------------------------------------------------------
    static void antiPatterns() {
        System.out.println("--- 6. Anti-Patterns ---");

        // ANTI-PATTERN 1: Using Optional as a method parameter
        // Optional should be a return type, not a parameter.
        // void process(Optional<String> name) -- forces callers to wrap unnecessarily

        // ANTI-PATTERN 2: isPresent + get — same as a null check, no improvement
        Optional<String> name = Optional.of("Alice");
        if (name.isPresent()) {
            System.out.println("isPresent+get (antipattern): " + name.get()); // use ifPresent instead
        }

        // ANTI-PATTERN 3: orElse with null — defeats the purpose
        // String result = name.orElse(null);  -- just use nullable return type

        // ANTI-PATTERN 4: Wrapping collections — use empty collection, not Optional<List>
        // Optional<List<Employee>> -- WRONG
        // List<Employee> -- RIGHT (return Collections.emptyList() when empty)

        // ANTI-PATTERN 5: Optional in entity fields / serialization
        // Optional is not Serializable. Do not use it in JPA entities or DTOs.

        // ANTI-PATTERN 6: orElse with expensive call when not needed
        Employee dummyEmployee = new Employee(0, "Dummy", Department.HR, 0, java.time.LocalDate.now());
        Optional<Employee> found = Optional.of(dummyEmployee);
        // This always calls createExpensiveDefault(), even when found is present:
        found.orElse(createExpensiveDefault());   // BAD
        found.orElseGet(() -> createExpensiveDefault()); // GOOD — lazy

        System.out.println("See comments in source for the anti-pattern explanations.");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Real-World Usage — service layer returning Optional
    // -------------------------------------------------------------------------
    static void realWorldUsage() {
        System.out.println("--- 7. Real-World Usage ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Service method returns Optional — caller decides how to handle absence
        String report = findEmployeeById(employees, 4L)
                .filter(Employee::isActive)
                .map(e -> String.format("Employee: %s | Dept: %s | Salary: £%.0f",
                        e.getName(), e.getDepartment(), e.getSalary()))
                .orElse("Employee not found or inactive");

        System.out.println(report);

        // Chain: find engineer → get their department → format a report
        String deptReport = employees.stream()
                .filter(e -> e.getSalary() > 100_000)
                .findFirst()
                .map(Employee::getDepartment)
                .map(dept -> "High earner department: " + dept)
                .orElse("No high earners found");
        System.out.println(deptReport);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    static Optional<Employee> findEmployeeById(List<Employee> employees, long id) {
        return employees.stream().filter(e -> e.getId() == id).findFirst();
    }

    static Optional<String> getManagerEmail(Employee employee) {
        // In reality this would look up the manager
        if (employee.getDepartment() == Department.ENGINEERING) {
            return Optional.of("engineering-mgr@company.com");
        }
        return Optional.empty();
    }

    static String expensiveComputation(String label) {
        System.out.println("  expensiveComputation called by " + label);
        return "computed";
    }

    static Employee createExpensiveDefault() {
        return new Employee(0, "Default", Department.HR, 0, java.time.LocalDate.now());
    }
}

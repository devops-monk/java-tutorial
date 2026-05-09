package com.devopsmonk.java8.ch04_functional;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;

import java.util.Arrays;
import java.util.List;
import java.util.function.*;

/**
 * Chapter 4 — Functional Interfaces
 * Tutorial: https://devops-monk.com/tutorials/java8/functional-interfaces/
 *
 * Covers:
 *  - @FunctionalInterface contract
 *  - Predicate<T> and composition (and, or, negate)
 *  - Function<T,R> and composition (andThen, compose)
 *  - Consumer<T> and chaining (andThen)
 *  - Supplier<T> for lazy evaluation
 *  - BiFunction, UnaryOperator, BinaryOperator
 *  - Building a validation pipeline with Predicate
 */
public class FunctionalInterfaceExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 4: Functional Interfaces ===\n");

        predicateExamples();
        functionExamples();
        consumerExamples();
        supplierExamples();
        biFunctionAndOperators();
        validationPipeline();
    }

    // -------------------------------------------------------------------------
    // 1. Predicate<T> — boolean test + composition
    // -------------------------------------------------------------------------
    static void predicateExamples() {
        System.out.println("--- 1. Predicate<T> ---");

        List<Employee> employees = Employee.SampleData.employees();

        Predicate<Employee> isActive      = Employee::isActive;
        Predicate<Employee> isEngineer    = e -> e.getDepartment() == Department.ENGINEERING;
        Predicate<Employee> earnOver90k   = e -> e.getSalary() > 90_000;
        Predicate<Employee> joinedBefore2020 = e -> e.getJoinDate().getYear() < 2020;

        // and() — both must be true
        Predicate<Employee> seniorEngineer = isActive.and(isEngineer).and(earnOver90k);

        // or() — either can be true
        Predicate<Employee> engineerOrFinance = isEngineer.or(e -> e.getDepartment() == Department.FINANCE);

        // negate() — flip the result
        Predicate<Employee> isInactive = isActive.negate();

        System.out.println("Active engineers earning >90k:");
        employees.stream().filter(seniorEngineer)
                 .forEach(e -> System.out.println("  " + e.getName()));

        System.out.println("Inactive employees:");
        employees.stream().filter(isInactive)
                 .forEach(e -> System.out.println("  " + e.getName()));

        System.out.println("Engineers OR Finance who joined before 2020:");
        employees.stream().filter(engineerOrFinance.and(joinedBefore2020))
                 .forEach(e -> System.out.printf("  %-15s %s%n", e.getName(), e.getDepartment()));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Function<T,R> — transform + compose
    // -------------------------------------------------------------------------
    static void functionExamples() {
        System.out.println("--- 2. Function<T,R> ---");

        // Basic transformation
        Function<String, Integer> strLen  = String::length;
        Function<Integer, String> intToStr = n -> "Number: " + n;

        // andThen: apply strLen first, then intToStr
        Function<String, String> strLenFormatted = strLen.andThen(intToStr);
        System.out.println(strLenFormatted.apply("Java 8"));

        // compose: apply intToStr first (inner), then strLen (outer) — reverse of andThen
        // strLen.compose(intToStr) means: first intToStr, then strLen
        Function<Integer, Integer> intStrLen = strLen.compose(intToStr);
        System.out.println("Length of 'Number: 42': " + intStrLen.apply(42));

        // Real-world: payroll processing pipeline
        Function<Employee, Double>  getSalary     = Employee::getSalary;
        Function<Double, Double>    applyTax      = salary -> salary * 0.78;  // 22% tax
        Function<Double, String>    formatPayslip = net ->
                String.format("Net pay: £%.2f", net);

        Function<Employee, String> payslip = getSalary.andThen(applyTax).andThen(formatPayslip);

        Employee.SampleData.employees().stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .limit(3)
                .forEach(e -> System.out.printf("  %-15s  %s%n", e.getName(), payslip.apply(e)));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Consumer<T> — side effects + chaining
    // -------------------------------------------------------------------------
    static void consumerExamples() {
        System.out.println("--- 3. Consumer<T> ---");

        Consumer<Employee> printName     = e -> System.out.print("  Name: " + e.getName());
        Consumer<Employee> printDept     = e -> System.out.print("  | Dept: " + e.getDepartment());
        Consumer<Employee> printSalary   = e -> System.out.println("  | Salary: £" + (int) e.getSalary());
        Consumer<Employee> printNewline  = e -> System.out.println();

        // andThen chains consumers — all run in sequence on the same element
        Consumer<Employee> printSummary = printName.andThen(printDept).andThen(printSalary);

        System.out.println("Employee summary (Consumer chain):");
        Employee.SampleData.employees().stream()
                .filter(Employee::isActive)
                .limit(3)
                .forEach(printSummary);

        // BiConsumer — two inputs, no return
        BiConsumer<String, Employee> auditLog =
                (action, emp) -> System.out.printf("  AUDIT: %s performed on %s%n", action, emp.getName());

        auditLog.accept("SALARY_INCREASE", Employee.SampleData.employees().get(0));
        auditLog.accept("DEACTIVATE",      Employee.SampleData.employees().get(14));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Supplier<T> — lazy evaluation / deferred creation
    // -------------------------------------------------------------------------
    static void supplierExamples() {
        System.out.println("--- 4. Supplier<T> ---");

        // Supplier defers creation until get() is called
        Supplier<List<Employee>> employeeLoader = Employee.SampleData::employees;

        System.out.println("Supplier created — employees NOT loaded yet");
        List<Employee> employees = employeeLoader.get();  // loads now
        System.out.println("Employees loaded: " + employees.size());

        // Lazy default value — only compute if needed
        Supplier<String> expensiveDefault = () -> {
            System.out.println("  (computing expensive default...)");
            return "DEFAULT_REPORT";
        };

        String report = findReportForDept(employees, Department.HR, expensiveDefault);
        System.out.println("Report: " + report);

        // IntSupplier, LongSupplier, DoubleSupplier — primitive specialisations
        IntSupplier nextId = new IntSupplier() {
            int current = 100;
            @Override public int getAsInt() { return current++; }
        };
        System.out.println("Next IDs: " + nextId.getAsInt() + ", " + nextId.getAsInt() + ", " + nextId.getAsInt());

        System.out.println();
    }

    static String findReportForDept(List<Employee> employees, Department dept, Supplier<String> defaultReport) {
        return employees.stream()
                .filter(e -> e.getDepartment() == dept)
                .map(e -> e.getName() + "'s dept report")
                .findFirst()
                .orElseGet(defaultReport);  // Supplier called only if empty
    }

    // -------------------------------------------------------------------------
    // 5. BiFunction, UnaryOperator, BinaryOperator
    // -------------------------------------------------------------------------
    static void biFunctionAndOperators() {
        System.out.println("--- 5. BiFunction, UnaryOperator, BinaryOperator ---");

        // BiFunction<T, U, R> — two inputs, one output (different types)
        BiFunction<Employee, Double, String> raiseNotice =
                (emp, pct) -> String.format("%s gets a %.0f%% raise: £%.0f → £%.0f",
                        emp.getName(), pct * 100, emp.getSalary(), emp.getSalary() * (1 + pct));

        Employee alice = Employee.SampleData.employees().get(0);
        System.out.println(raiseNotice.apply(alice, 0.15));

        // UnaryOperator<T> — same type in and out (specialisation of Function<T,T>)
        UnaryOperator<String> shout = s -> s.toUpperCase() + "!";
        UnaryOperator<String> trim  = String::trim;
        UnaryOperator<String> clean = trim.andThen(shout);   // compose via andThen

        System.out.println(clean.apply("  hello world  "));

        // BinaryOperator<T> — two inputs same type, one output same type
        BinaryOperator<Double> highestSalary = (a, b) -> a > b ? a : b;
        double max = Employee.SampleData.employees().stream()
                .map(Employee::getSalary)
                .reduce(0.0, highestSalary);
        System.out.println("Highest salary: £" + (int) max);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Validation Pipeline — Predicate composition in real code
    // -------------------------------------------------------------------------
    static void validationPipeline() {
        System.out.println("--- 6. Validation Pipeline ---");

        // Model a set of business rules as named predicates
        Predicate<Employee> hasName     = e -> e.getName() != null && !e.getName().isBlank();
        Predicate<Employee> hasDept     = e -> e.getDepartment() != null;
        Predicate<Employee> validSalary = e -> e.getSalary() > 30_000 && e.getSalary() < 500_000;
        Predicate<Employee> hasJoinDate = e -> e.getJoinDate() != null;

        // All rules must pass
        Predicate<Employee> isValid = hasName.and(hasDept).and(validSalary).and(hasJoinDate);

        // Build a validator that reports the first failing rule
        List<Predicate<Employee>> rules = Arrays.asList(hasName, hasDept, validSalary, hasJoinDate);
        List<String> ruleNames = Arrays.asList("hasName", "hasDept", "validSalary", "hasJoinDate");

        Function<Employee, String> validate = emp -> {
            for (int i = 0; i < rules.size(); i++) {
                if (!rules.get(i).test(emp)) return "INVALID: " + ruleNames.get(i) + " failed";
            }
            return "VALID";
        };

        Employee.SampleData.employees().forEach(e ->
                System.out.printf("  %-15s → %s%n", e.getName(), validate.apply(e)));
    }
}

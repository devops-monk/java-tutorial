package com.devopsmonk.java8.ch12_collections;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chapter 12 — Collections and Map Enhancements
 * Tutorial: https://devops-monk.com/tutorials/java8/collections-maps/
 *
 * Covers:
 *  - List/Collection: forEach, removeIf, replaceAll, sort
 *  - Map: getOrDefault, putIfAbsent, forEach, merge, compute, computeIfAbsent, computeIfPresent, replaceAll
 *  - Map.Entry comparators
 *  - Building a frequency map, an employee index, and a cache with computeIfAbsent
 */
public class CollectionMapExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 12: Collections and Map Enhancements ===\n");

        listEnhancements();
        mapBasicEnhancements();
        mapMerge();
        mapCompute();
        mapComputeIfAbsent();
        mapReplaceAll();
        mapEntryComparators();
        realWorldScenarios();
    }

    // -------------------------------------------------------------------------
    // 1. List Enhancements
    // -------------------------------------------------------------------------
    static void listEnhancements() {
        System.out.println("--- 1. List Enhancements ---");

        List<String> tags = new ArrayList<>(Arrays.asList("java", "streams", "", "lambdas", "  ", "optional"));

        // forEach — internal iteration (vs external for-loop)
        System.out.print("Tags: ");
        tags.forEach(t -> System.out.print("[" + t + "] "));
        System.out.println();

        // removeIf — remove all blank tags
        tags.removeIf(String::isBlank);
        System.out.println("After removeIf(blank): " + tags);

        // replaceAll — transform each element in-place (UnaryOperator)
        tags.replaceAll(t -> "#" + t.toUpperCase());
        System.out.println("After replaceAll(hashUpper): " + tags);

        // sort with Comparator (default method on List since Java 8)
        tags.sort(Comparator.comparing(s -> s.substring(1)));  // sort by tag text ignoring #
        System.out.println("After sort: " + tags);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Map: getOrDefault, putIfAbsent, forEach
    // -------------------------------------------------------------------------
    static void mapBasicEnhancements() {
        System.out.println("--- 2. Map Basic Enhancements ---");

        Map<String, Integer> wordCount = new HashMap<>();
        wordCount.put("java", 10);
        wordCount.put("streams", 5);

        // getOrDefault — no more null checks
        int javaCount    = wordCount.getOrDefault("java",    0);
        int missingCount = wordCount.getOrDefault("missing", 0);
        System.out.println("java:    " + javaCount);
        System.out.println("missing: " + missingCount);

        // putIfAbsent — only inserts if key not already present
        wordCount.putIfAbsent("java", 999);   // ignored — java already exists
        wordCount.putIfAbsent("kotlin", 3);   // inserted
        System.out.println("After putIfAbsent: " + wordCount);

        // forEach — iterate over entries without Map.Entry boilerplate
        System.out.println("Word counts:");
        wordCount.forEach((word, count) -> System.out.printf("  %-10s %d%n", word, count));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. merge — combine existing value with new value (or insert if absent)
    // -------------------------------------------------------------------------
    static void mapMerge() {
        System.out.println("--- 3. Map.merge ---");

        // Build a word frequency map using merge
        String[] words = {"java", "streams", "java", "lambda", "streams", "java"};

        Map<String, Integer> freq = new HashMap<>();
        for (String word : words) {
            // If key absent: insert value 1
            // If key present: apply remappingFunction to (existing, new) → merged value
            freq.merge(word, 1, Integer::sum);
        }
        System.out.println("Word frequencies: " + freq);

        // Aggregate salary per department using merge
        Map<Department, Double> deptPayroll = new HashMap<>();
        Employee.SampleData.employees().forEach(e ->
                deptPayroll.merge(e.getDepartment(), e.getSalary(), Double::sum)
        );
        System.out.println("\nPayroll per department:");
        deptPayroll.entrySet().stream()
                   .sorted(Map.Entry.<Department, Double>comparingByValue().reversed())
                   .forEach(en -> System.out.printf("  %-12s £%.0f%n", en.getKey(), en.getValue()));

        // merge with a string accumulator — build comma-separated names per dept
        Map<Department, String> namesByDept = new HashMap<>();
        Employee.SampleData.employees().forEach(e ->
                namesByDept.merge(e.getDepartment(), e.getName(),
                        (existing, newName) -> existing + ", " + newName)
        );
        System.out.println("\nEngineering: " + namesByDept.get(Department.ENGINEERING));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. compute, computeIfPresent — fine-grained update logic
    // -------------------------------------------------------------------------
    static void mapCompute() {
        System.out.println("--- 4. compute and computeIfPresent ---");

        Map<String, List<String>> skillMap = new HashMap<>();

        // compute — always runs the BiFunction; can insert, update, or remove
        // If BiFunction returns null: removes the key
        skillMap.compute("Alice", (key, existing) -> {
            if (existing == null) existing = new ArrayList<>();
            existing.add("Java");
            return existing;
        });
        skillMap.compute("Alice", (key, existing) -> {
            existing.add("Kubernetes");
            return existing;
        });
        System.out.println("Alice's skills after compute: " + skillMap.get("Alice"));

        // computeIfPresent — only runs if key already exists (safe update)
        skillMap.computeIfPresent("Alice", (key, list) -> {
            list.add("Terraform");
            return list;
        });
        skillMap.computeIfPresent("Bob", (key, list) -> {   // Bob doesn't exist — no-op
            list.add("Python");
            return list;
        });
        System.out.println("Alice after computeIfPresent:   " + skillMap.get("Alice"));
        System.out.println("Bob after computeIfPresent:     " + skillMap.get("Bob"));  // null

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. computeIfAbsent — lazy initialisation / cache pattern
    // -------------------------------------------------------------------------
    static void mapComputeIfAbsent() {
        System.out.println("--- 5. computeIfAbsent ---");

        // Classic use case: grouping — initialise the list lazily
        Map<Department, List<Employee>> byDept = new HashMap<>();
        Employee.SampleData.employees().forEach(e ->
                byDept.computeIfAbsent(e.getDepartment(), dept -> new ArrayList<>())
                      .add(e)
        );
        System.out.println("Groups built with computeIfAbsent:");
        byDept.forEach((dept, emps) ->
                System.out.printf("  %-12s %d employees%n", dept, emps.size()));

        // Cache / memoization pattern
        Map<Long, Employee> cache = new HashMap<>();
        List<Employee> source = Employee.SampleData.employees();

        Employee fetched = cache.computeIfAbsent(1L, id -> {
            System.out.println("  Cache miss — loading id=" + id);
            return source.stream().filter(e -> e.getId() == id).findFirst().orElse(null);
        });
        System.out.println("Fetched: " + fetched.getName());

        // Second call — value is already cached, function NOT called
        Employee cached = cache.computeIfAbsent(1L, id -> {
            System.out.println("  Cache miss — loading id=" + id);  // won't print
            return null;
        });
        System.out.println("Cached:  " + cached.getName());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. replaceAll on Map — transform all values in-place
    // -------------------------------------------------------------------------
    static void mapReplaceAll() {
        System.out.println("--- 6. Map.replaceAll ---");

        Map<String, Integer> scores = new HashMap<>();
        scores.put("Alice", 85);
        scores.put("Bob", 72);
        scores.put("Carol", 90);

        System.out.println("Before: " + scores);

        // Apply a 10% bonus to every score
        scores.replaceAll((name, score) -> (int) (score * 1.10));
        System.out.println("After 10% bonus: " + scores);

        // Cap at 100
        scores.replaceAll((name, score) -> Math.min(score, 100));
        System.out.println("After cap at 100: " + scores);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Map.Entry comparators — sort a Map by value or key
    // -------------------------------------------------------------------------
    static void mapEntryComparators() {
        System.out.println("--- 7. Map.Entry Comparators ---");

        Map<String, Double> salaryMap = Employee.SampleData.employees().stream()
                .collect(Collectors.toMap(Employee::getName, Employee::getSalary));

        // Sort by value descending
        System.out.println("Top 5 earners:");
        salaryMap.entrySet().stream()
                 .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                 .limit(5)
                 .forEach(e -> System.out.printf("  %-15s £%.0f%n", e.getKey(), e.getValue()));

        // Sort by key alphabetically
        System.out.println("\nAlphabetical:");
        salaryMap.entrySet().stream()
                 .sorted(Map.Entry.comparingByKey())
                 .limit(5)
                 .forEach(e -> System.out.printf("  %-15s £%.0f%n", e.getKey(), e.getValue()));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 8. Real-World Scenarios
    // -------------------------------------------------------------------------
    static void realWorldScenarios() {
        System.out.println("--- 8. Real-World: Department Budget Tracker ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Build a budget snapshot per department using merge
        Map<Department, DeptBudget> budgets = new HashMap<>();
        employees.forEach(e -> {
            budgets.computeIfAbsent(e.getDepartment(), dept -> new DeptBudget(dept.name()));
            budgets.get(e.getDepartment()).addSalary(e.getSalary(), e.isActive());
        });

        System.out.printf("%-14s %6s %10s %10s%n", "Department", "Count", "Total", "Active");
        System.out.println("-".repeat(44));
        budgets.entrySet().stream()
               .sorted(Map.Entry.<Department, DeptBudget>comparingByValue(
                       Comparator.comparingDouble(b -> b.total)).reversed())
               .forEach(e -> System.out.printf("%-14s %6d %10.0f %10.0f%n",
                       e.getKey(), e.getValue().count, e.getValue().total, e.getValue().activeTotal));
    }

    static class DeptBudget {
        final String name;
        int count;
        double total;
        double activeTotal;

        DeptBudget(String name) { this.name = name; }

        void addSalary(double salary, boolean active) {
            count++;
            total += salary;
            if (active) activeTotal += salary;
        }
    }
}

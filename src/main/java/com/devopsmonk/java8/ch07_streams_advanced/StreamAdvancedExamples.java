package com.devopsmonk.java8.ch07_streams_advanced;

import com.devopsmonk.java8.model.Department;
import com.devopsmonk.java8.model.Employee;
import com.devopsmonk.java8.model.Order;
import com.devopsmonk.java8.model.OrderItem;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

/**
 * Chapter 7 — Advanced Streams: flatMap, Collectors, Grouping, Partitioning
 * Tutorial: https://devops-monk.com/tutorials/java8/streams-advanced/
 *
 * Covers:
 *  - flatMap — flattening nested structures
 *  - Collectors: toList, toSet, toMap, joining, counting, summingDouble
 *  - groupingBy — group elements by a classifier
 *  - partitioningBy — split into two groups
 *  - downstream collectors — counting, averaging, mapping, summarizing
 *  - Custom Collector
 *  - toUnmodifiableList (Java 10 preview pattern with Java 8 workaround)
 */
public class StreamAdvancedExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 7: Advanced Streams ===\n");

        flatMapExamples();
        collectorsExamples();
        groupingByExamples();
        partitioningByExamples();
        downstreamCollectors();
        customCollector();
        toMapPitfalls();
    }

    // -------------------------------------------------------------------------
    // 1. flatMap — flatten nested streams into one stream
    // -------------------------------------------------------------------------
    static void flatMapExamples() {
        System.out.println("--- 1. flatMap ---");

        // Problem: each Order has a List<OrderItem>.
        // Stream<Order> → Stream<List<OrderItem>> (with map) is two levels deep.
        // Stream<Order> → Stream<OrderItem> (with flatMap) is flat.

        List<Order> orders = Order.sampleOrders();

        // With map — gives Stream<List<OrderItem>>, hard to work with
        // orders.stream().map(Order::getItems)  // Stream<List<OrderItem>>

        // With flatMap — gives Stream<OrderItem> directly
        List<String> allProductNames = orders.stream()
                .flatMap(o -> o.getItems().stream())     // Stream<Order> → Stream<OrderItem>
                .map(item -> item.getProduct().getName()) // Stream<OrderItem> → Stream<String>
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        System.out.println("Distinct products across all orders: " + allProductNames);

        // Total revenue across all orders (flatMap + mapToDouble)
        double totalRevenue = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToDouble(OrderItem::getLineTotal)
                .sum();
        System.out.printf("Total revenue: £%.2f%n", totalRevenue);

        // flatMap on strings — all words from all employee names
        List<String> allWords = Employee.SampleData.employees().stream()
                .map(Employee::getName)
                .flatMap(name -> Arrays.stream(name.split(" ")))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        System.out.println("All name parts: " + allWords);

        // flatMapToInt — combining primitive flatMap
        int totalStock = Arrays.asList(
                Arrays.asList(10, 20, 30),
                Arrays.asList(5, 15),
                Arrays.asList(100)
        ).stream()
         .flatMapToInt(list -> list.stream().mapToInt(Integer::intValue))
         .sum();
        System.out.println("Total stock sum: " + totalStock);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Core Collectors
    // -------------------------------------------------------------------------
    static void collectorsExamples() {
        System.out.println("--- 2. Core Collectors ---");

        List<Employee> employees = Employee.SampleData.employees();

        // toList, toSet, toCollection
        List<String> names = employees.stream()
                .map(Employee::getName).collect(Collectors.toList());
        Set<Department> depts = employees.stream()
                .map(Employee::getDepartment).collect(Collectors.toSet());
        System.out.println("Names count: " + names.size() + " | Depts: " + depts);

        // joining — string concatenation with delimiter, prefix, suffix
        String namesCsv = employees.stream()
                .map(Employee::getName)
                .collect(Collectors.joining(", "));
        System.out.println("Names CSV: " + namesCsv);

        String formattedList = employees.stream()
                .filter(Employee::isActive)
                .map(Employee::getName)
                .collect(Collectors.joining("\n  - ", "Active employees:\n  - ", ""));
        System.out.println(formattedList);

        // counting
        long engineerCount = employees.stream()
                .filter(e -> e.getDepartment() == Department.ENGINEERING)
                .collect(Collectors.counting());
        System.out.println("Engineer count: " + engineerCount);

        // summingDouble / averagingDouble
        double totalPayroll = employees.stream()
                .collect(Collectors.summingDouble(Employee::getSalary));
        double avgSalary = employees.stream()
                .collect(Collectors.averagingDouble(Employee::getSalary));
        System.out.printf("Total payroll: £%.0f | Avg: £%.0f%n", totalPayroll, avgSalary);

        // summarizingDouble — all stats in one pass
        DoubleSummaryStatistics stats = employees.stream()
                .collect(Collectors.summarizingDouble(Employee::getSalary));
        System.out.printf("Salary stats: min=£%.0f  max=£%.0f  avg=£%.0f  count=%d%n",
                stats.getMin(), stats.getMax(), stats.getAverage(), stats.getCount());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. groupingBy — classify elements into a Map
    // -------------------------------------------------------------------------
    static void groupingByExamples() {
        System.out.println("--- 3. groupingBy ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Group by department → Map<Department, List<Employee>>
        Map<Department, List<Employee>> byDept =
                employees.stream().collect(Collectors.groupingBy(Employee::getDepartment));

        byDept.forEach((dept, emps) ->
                System.out.printf("  %-12s : %d employees%n", dept, emps.size()));

        // Group by department, but only collect names
        Map<Department, List<String>> namesByDept = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDepartment,
                        Collectors.mapping(Employee::getName, Collectors.toList())
                ));
        System.out.println("\nEngineering team: " + namesByDept.get(Department.ENGINEERING));

        // Two-level grouping — department → active status → employees
        Map<Department, Map<Boolean, List<Employee>>> byDeptAndActive = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDepartment,
                        Collectors.groupingBy(Employee::isActive)
                ));
        byDeptAndActive.forEach((dept, activeMap) ->
                System.out.printf("  %-12s : %d active, %d inactive%n",
                        dept,
                        activeMap.getOrDefault(true,  Collections.emptyList()).size(),
                        activeMap.getOrDefault(false, Collections.emptyList()).size()));

        // Group orders by status and sum revenue per status
        Map<Order.Status, Double> revenueByStatus = Order.sampleOrders().stream()
                .collect(Collectors.groupingBy(
                        Order::getStatus,
                        Collectors.summingDouble(Order::getTotal)
                ));
        System.out.println("\nRevenue by order status:");
        revenueByStatus.forEach((status, revenue) ->
                System.out.printf("  %-12s £%.2f%n", status, revenue));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. partitioningBy — exactly two groups: true and false
    // -------------------------------------------------------------------------
    static void partitioningByExamples() {
        System.out.println("--- 4. partitioningBy ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Partition into high earners vs everyone else
        Map<Boolean, List<Employee>> partition = employees.stream()
                .collect(Collectors.partitioningBy(e -> e.getSalary() > 90_000));

        System.out.println("High earners (>90k): " +
                partition.get(true).stream().map(Employee::getName).collect(Collectors.joining(", ")));
        System.out.println("Others:              " +
                partition.get(false).stream().map(Employee::getName).collect(Collectors.joining(", ")));

        // Partition active vs inactive, with count downstream
        Map<Boolean, Long> activeCount = employees.stream()
                .collect(Collectors.partitioningBy(Employee::isActive, Collectors.counting()));
        System.out.println("\nActive: " + activeCount.get(true) + " | Inactive: " + activeCount.get(false));

        // Partition orders by delivered vs not
        Map<Boolean, List<Order>> deliveredOrders = Order.sampleOrders().stream()
                .collect(Collectors.partitioningBy(o -> o.getStatus() == Order.Status.DELIVERED));
        System.out.println("Delivered orders: " + deliveredOrders.get(true).size());
        System.out.println("Pending/other:    " + deliveredOrders.get(false).size());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Downstream Collectors — aggregate inside groups
    // -------------------------------------------------------------------------
    static void downstreamCollectors() {
        System.out.println("--- 5. Downstream Collectors ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Average salary per department
        Map<Department, Double> avgByDept = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDepartment,
                        Collectors.averagingDouble(Employee::getSalary)
                ));
        System.out.println("Average salary by department:");
        avgByDept.entrySet().stream()
                 .sorted(Map.Entry.<Department, Double>comparingByValue().reversed())
                 .forEach(e -> System.out.printf("  %-12s £%.0f%n", e.getKey(), e.getValue()));

        // Summary statistics per department
        Map<Department, DoubleSummaryStatistics> statsPerDept = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDepartment,
                        Collectors.summarizingDouble(Employee::getSalary)
                ));
        System.out.println("\nEngineering stats: " + statsPerDept.get(Department.ENGINEERING));

        // Count per department (as TreeMap for sorted output)
        Map<Department, Long> countByDept = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDepartment,
                        TreeMap::new,
                        Collectors.counting()
                ));
        System.out.println("\nHeadcount per department: " + countByDept);

        // collectingAndThen — transform the result of another collector
        Map<Department, Optional<Employee>> highestPaidPerDept = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getDepartment,
                        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))
                ));
        System.out.println("\nHighest paid per department:");
        highestPaidPerDept.forEach((dept, empOpt) ->
                empOpt.ifPresent(e -> System.out.printf("  %-12s %s (£%.0f)%n",
                        dept, e.getName(), e.getSalary())));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Custom Collector — salary report with median
    // -------------------------------------------------------------------------
    static void customCollector() {
        System.out.println("--- 6. Custom Collector ---");

        // Collectors.of(supplier, accumulator, combiner, finisher)
        // Goal: collect salaries and compute median

        Collector<Employee, List<Double>, String> medianReport =
                Collector.of(
                        ArrayList::new,                          // supplier: create accumulator
                        (list, emp) -> list.add(emp.getSalary()), // accumulator: add salary
                        (a, b) -> { a.addAll(b); return a; },    // combiner: merge parallel results
                        list -> {                                  // finisher: compute median
                            Collections.sort(list);
                            int n = list.size();
                            double median = (n % 2 == 0)
                                    ? (list.get(n / 2 - 1) + list.get(n / 2)) / 2.0
                                    : list.get(n / 2);
                            return String.format("count=%d, median=£%.0f, min=£%.0f, max=£%.0f",
                                    n, median, list.get(0), list.get(n - 1));
                        }
                );

        String report = Employee.SampleData.employees().stream()
                .filter(Employee::isActive)
                .collect(medianReport);
        System.out.println("Active salary report: " + report);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. toMap — common pitfall: duplicate keys
    // -------------------------------------------------------------------------
    static void toMapPitfalls() {
        System.out.println("--- 7. toMap and the Duplicate Key Pitfall ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Basic toMap — will throw if two employees have the same name
        Map<Long, String> idToName = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        System.out.println("ID 1 → " + idToName.get(1L));

        // toMap with merge function — handles duplicate keys
        // Group salaries by department as a comma-separated string
        Map<Department, String> deptNameList = employees.stream()
                .collect(Collectors.toMap(
                        Employee::getDepartment,
                        Employee::getName,
                        (existing, next) -> existing + ", " + next   // merge function
                ));
        System.out.println("Engineering: " + deptNameList.get(Department.ENGINEERING));

        // toMap with a specific Map implementation (LinkedHashMap for insertion order)
        Map<String, Double> nameSalary = employees.stream()
                .limit(5)
                .collect(Collectors.toMap(
                        Employee::getName,
                        Employee::getSalary,
                        (a, b) -> a,        // keep first on duplicate
                        LinkedHashMap::new  // preserve insertion order
                ));
        nameSalary.forEach((name, sal) ->
                System.out.printf("  %-15s £%.0f%n", name, sal));

        System.out.println();
    }
}

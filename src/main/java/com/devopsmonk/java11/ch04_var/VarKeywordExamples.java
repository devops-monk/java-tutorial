package com.devopsmonk.java11.ch04_var;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chapter 04 — var Keyword: Local Variable Type Inference (JEP 286 + JEP 323)
 * Tutorial: https://devops-monk.com/tutorials/java11/var-keyword/
 *
 * JEP 286 (Java 10): var for local variables
 * JEP 323 (Java 11): var in lambda parameters (enables annotations on params)
 *
 * var is compile-time only — the bytecode is identical to explicit types.
 * The inferred type is always the most specific type the compiler can determine.
 */
public class VarKeywordExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 04: var Keyword ===\n");

        basicLocalVariables();
        varInLoops();
        varWithGenerics();
        varInTryWithResources();
        varInLambdaParams();
        whereVarIsNotAllowed();
        styleGuidelines();
    }

    // -------------------------------------------------------------------------
    // 1. Basic Local Variables — var infers the declared type
    // -------------------------------------------------------------------------
    static void basicLocalVariables() {
        System.out.println("--- 1. Basic Local Variables ---");

        // var infers String — bytecode is exactly the same as: String name = "Alice";
        var name = "Alice";
        var age  = 30;
        var salary = 95_000.0;
        var active = true;

        System.out.printf("  name=%s  age=%d  salary=%.0f  active=%b%n", name, age, salary, active);
        System.out.println("  Inferred types: " + ((Object)name).getClass().getSimpleName()
                + ", " + ((Object)age).getClass().getSimpleName()
                + ", " + ((Object)salary).getClass().getSimpleName());

        // var with objects — infers the concrete type, not the interface
        var list = new ArrayList<String>();  // inferred as ArrayList<String>, not List<String>
        list.add("Java");
        list.add("11");
        System.out.println("  list type: " + list.getClass().getSimpleName() + " → " + list);

        // var with Map.entry — avoids the verbose Map.Entry<String, Integer> spelling
        var entry = Map.entry("threads", 8);
        System.out.println("  entry: " + entry.getKey() + " = " + entry.getValue());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. var in Loops — biggest readability win over explicit types
    // -------------------------------------------------------------------------
    static void varInLoops() {
        System.out.println("--- 2. var in Loops ---");

        var employees = List.of("Alice:ENGINEERING:95000", "Bob:PRODUCT:82000",
                                "Carol:DESIGN:78000",    "Dave:ENGINEERING:91000");

        // Enhanced for loop — var infers String
        System.out.println("  Enhanced for:");
        for (var emp : employees) {
            var parts  = emp.split(":");
            var name   = parts[0];
            var dept   = parts[1];
            var salary = Double.parseDouble(parts[2]);
            System.out.printf("    %-6s  %-12s  £%.0f%n", name, dept, salary);
        }

        // Index loop — var infers int
        System.out.print("  Indexed for: ");
        for (var i = 0; i < employees.size(); i++) {
            System.out.print(i + " ");
        }
        System.out.println();

        // Iterator loop — var avoids Iterator<String> spelling
        var iter = employees.iterator();
        System.out.print("  Iterator:    ");
        while (iter.hasNext()) {
            var emp = iter.next();
            System.out.print(emp.split(":")[0] + " ");
        }
        System.out.println();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. var with Generics — avoids long type spellings
    // -------------------------------------------------------------------------
    static void varWithGenerics() {
        System.out.println("--- 3. var with Generics ---");

        // Without var: Map<String, List<Map<String, Integer>>> orgChart = new HashMap<>();
        var orgChart = new HashMap<String, List<Map<String, Integer>>>();
        orgChart.computeIfAbsent("Engineering", k -> new ArrayList<>())
                .add(Map.of("Alice", 95000, "Dave", 91000));

        System.out.println("  orgChart keys: " + orgChart.keySet());

        // var makes the stream pipeline easier to build incrementally
        var grouped = List.of("Alice:ENG", "Bob:PROD", "Carol:ENG", "Dave:PROD")
                .stream()
                .collect(Collectors.groupingBy(s -> s.split(":")[1]));

        System.out.println("  grouped type: " + grouped.getClass().getSimpleName());
        grouped.forEach((dept, members) ->
                System.out.println("  " + dept + ": " + members));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. var in try-with-resources — avoids repeating the type name
    // -------------------------------------------------------------------------
    static void varInTryWithResources() throws Exception {
        System.out.println("--- 4. var in try-with-resources ---");

        String csv = "Alice,ENGINEERING,95000\nBob,PRODUCT,82000\nCarol,DESIGN,78000";

        // var infers BufferedReader
        try (var reader = new BufferedReader(new StringReader(csv))) {
            var line = reader.readLine();
            while (line != null) {
                var parts  = line.split(",");
                var name   = parts[0];
                var dept   = parts[1];
                var salary = Integer.parseInt(parts[2]);
                System.out.printf("  %-6s %-12s £%,d%n", name, dept, salary);
                line = reader.readLine();
            }
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. var in Lambda Parameters (JEP 323 — Java 11 only)
    //
    // Why? The only reason to use var in a lambda is to add an annotation,
    // since you can't annotate an inferred parameter without naming a type.
    // -------------------------------------------------------------------------
    static void varInLambdaParams() {
        System.out.println("--- 5. var in Lambda Parameters (JEP 323) ---");

        var names = List.of("alice", "bob", "carol", "dave");

        // Plain lambda — no var needed here
        names.stream()
             .map(s -> s.toUpperCase())
             .forEach(s -> System.out.print(s + " "));
        System.out.println();

        // With var — identical behaviour, but now you CAN add annotations:
        // (@NonNull var s) -> s.toUpperCase()
        // This is the primary use case for var in lambdas.
        names.stream()
             .map((var s) -> s.toUpperCase())  // var enables annotation support
             .forEach((var s) -> System.out.print(s + " "));
        System.out.println();

        // Practical example — annotating for null safety tools (conceptual)
        Function<String, String> upper = (var s) -> s.strip().toUpperCase();
        System.out.println("  upper(\"  hello  \") = " + upper.apply("  hello  "));

        // NOTE: You cannot mix var and explicit types in the same lambda:
        // (var x, String y) -> ... // DOES NOT COMPILE

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Where var Is NOT Allowed
    // -------------------------------------------------------------------------
    static void whereVarIsNotAllowed() {
        System.out.println("--- 6. Where var Is NOT Allowed ---");

        System.out.println(
            "  ✗ Field declarations:         class Foo { var x = 5; }        — won't compile\n" +
            "  ✗ Method parameters:          void foo(var x) { }              — won't compile\n" +
            "  ✗ Method return types:        var foo() { return 5; }          — won't compile\n" +
            "  ✗ Catch clause variable:      catch (var e) { }                — won't compile\n" +
            "  ✗ null initialiser:           var x = null;                    — won't compile\n" +
            "  ✗ Array initialiser shorthand: var arr = {1, 2, 3};            — won't compile\n" +
            "  ✗ No initialiser:             var x;                           — won't compile\n\n" +
            "  ✓ Local variables with an initialiser — that's the only allowed position.\n" +
            "  ✓ For-loop index and enhanced-for variable.\n" +
            "  ✓ try-with-resources variable.\n" +
            "  ✓ Lambda parameters (Java 11+, allows annotations).\n"
        );
    }

    // -------------------------------------------------------------------------
    // 7. Style Guidelines — when to use var vs explicit types
    // -------------------------------------------------------------------------
    static void styleGuidelines() {
        System.out.println("--- 7. Style Guidelines ---");

        System.out.println(
            "  USE var when:\n" +
            "    ✓ The right-hand side already makes the type obvious:\n" +
            "        var list = new ArrayList<String>()    — ArrayList is right there\n" +
            "        var conn = dataSource.getConnection() — reader knows what getConnection() returns\n" +
            "        for (var entry : map.entrySet())      — entrySet tells you it's Map.Entry\n\n" +
            "    ✓ The type is a long generic spelling that adds no information:\n" +
            "        var cache = new HashMap<String, List<Integer>>()\n\n" +
            "  AVOID var when:\n" +
            "    ✗ The type isn't obvious from context:\n" +
            "        var result = process(config);         — what type is result?\n\n" +
            "    ✗ The inferred type is more specific than you want:\n" +
            "        var list = new ArrayList<String>();   // inferred ArrayList, not List\n" +
            "        // later: list = new LinkedList<>();  // COMPILE ERROR — type is locked to ArrayList\n\n" +
            "    ✗ With primitive literals where int/long/double confusion matters:\n" +
            "        var x = 1;    // int, not long or short\n" +
            "        var y = 1.0;  // double, not float\n"
        );
    }
}

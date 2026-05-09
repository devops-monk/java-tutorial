package com.devopsmonk.java8.ch13_async;

import com.devopsmonk.java8.model.Employee;
import com.devopsmonk.java8.model.Product;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Chapter 13 — CompletableFuture: Async Pipelines and Non-Blocking Composition
 * Tutorial: https://devops-monk.com/tutorials/java8/completablefuture/
 *
 * Covers:
 *  - Creating: supplyAsync, runAsync, completedFuture
 *  - Transforming: thenApply, thenCompose (flatMap)
 *  - Side effects: thenAccept, thenRun
 *  - Combining: thenCombine, allOf, anyOf
 *  - Error handling: exceptionally, handle, whenComplete
 *  - Real-world: product page fetch (price + details + stock in parallel)
 */
public class CompletableFutureExamples {

    static final ExecutorService pool = Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "cf-pool"); t.setDaemon(true); return t; });

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 13: CompletableFuture ===\n");

        try {
            creating();
            thenApplyAndAccept();
            thenCompose();
            thenCombine();
            allOfAndAnyOf();
            errorHandling();
            realWorldProductPage();
            parallelEmployeeProcessing();
        } finally {
            pool.shutdown();
        }
    }

    // -------------------------------------------------------------------------
    // 1. Creating CompletableFutures
    // -------------------------------------------------------------------------
    static void creating() throws Exception {
        System.out.println("--- 1. Creating CompletableFutures ---");

        // supplyAsync — runs in ForkJoinPool.commonPool(), returns a value
        CompletableFuture<String> hello = CompletableFuture.supplyAsync(() -> {
            simulateWork(50);
            return "Hello from async";
        });

        // runAsync — no return value (Void)
        CompletableFuture<Void> log = CompletableFuture.runAsync(() ->
                System.out.println("  [log] Background logging task ran"));

        // With custom executor — use your own thread pool, not commonPool
        CompletableFuture<Integer> withPool = CompletableFuture.supplyAsync(
                () -> 42, pool);

        // completedFuture — already-done future (useful for testing and defaults)
        CompletableFuture<String> done = CompletableFuture.completedFuture("immediate");

        System.out.println(hello.get());
        log.get();
        System.out.println("Custom pool result: " + withPool.get());
        System.out.println("Completed future:   " + done.get());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. thenApply (transform) and thenAccept (consume)
    // -------------------------------------------------------------------------
    static void thenApplyAndAccept() throws Exception {
        System.out.println("--- 2. thenApply and thenAccept ---");

        // thenApply: transform the result (like Stream.map) — non-blocking
        CompletableFuture<String> upper = CompletableFuture
                .supplyAsync(() -> "hello java")
                .thenApply(String::toUpperCase)
                .thenApply(s -> s + "!");

        System.out.println("thenApply: " + upper.get());

        // thenAccept: consume the result (like forEach) — returns CompletableFuture<Void>
        CompletableFuture<Void> printed = CompletableFuture
                .supplyAsync(() -> Employee.SampleData.employees())
                .thenAccept(employees -> {
                    long engineers = employees.stream()
                            .filter(e -> e.getDepartment() == com.devopsmonk.java8.model.Department.ENGINEERING)
                            .count();
                    System.out.println("thenAccept: engineers = " + engineers);
                });
        printed.get();

        // thenApplyAsync — runs the callback on a different thread
        CompletableFuture<String> async = CompletableFuture
                .supplyAsync(() -> "step1", pool)
                .thenApplyAsync(s -> s + "-step2", pool)
                .thenApplyAsync(s -> s + "-step3", pool);
        System.out.println("thenApplyAsync: " + async.get());

        // thenRun — run a Runnable after completion (no access to result)
        CompletableFuture.supplyAsync(() -> "data")
                .thenRun(() -> System.out.println("thenRun: cleanup after completion"))
                .get();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. thenCompose — chain dependent async operations (flatMap equivalent)
    // -------------------------------------------------------------------------
    static void thenCompose() throws Exception {
        System.out.println("--- 3. thenCompose (async flatMap) ---");

        // Without thenCompose — would give CompletableFuture<CompletableFuture<User>>
        // With thenCompose — flattens to CompletableFuture<Profile>

        CompletableFuture<String> pipeline = CompletableFuture
                .supplyAsync(() -> fetchUserId("alice"), pool)    // CompletableFuture<Long>
                .thenCompose(id -> fetchUserName(id, pool))       // CompletableFuture<String>
                .thenCompose(name -> fetchGreeting(name, pool));  // CompletableFuture<String>

        System.out.println("Composed pipeline: " + pipeline.get());

        // thenApply would give: CompletableFuture<CompletableFuture<String>> — nested, hard to use
        // thenCompose gives:    CompletableFuture<String> — flat, easy to chain further

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. thenCombine — combine two independent futures
    // -------------------------------------------------------------------------
    static void thenCombine() throws Exception {
        System.out.println("--- 4. thenCombine ---");

        // Two independent async tasks running in parallel
        CompletableFuture<Double> priceF  = CompletableFuture.supplyAsync(
                () -> { simulateWork(80); return 1299.99; }, pool);
        CompletableFuture<Double> taxRateF = CompletableFuture.supplyAsync(
                () -> { simulateWork(60); return 0.20; }, pool);

        // Combine when both complete — neither blocks the other
        CompletableFuture<String> invoice = priceF.thenCombine(taxRateF,
                (price, tax) -> String.format("Price: £%.2f + Tax: £%.2f = £%.2f",
                        price, price * tax, price * (1 + tax)));

        System.out.println("Invoice: " + invoice.get());

        // thenAcceptBoth — combine and consume (no return value)
        CompletableFuture<Void> logged = priceF.thenAcceptBoth(taxRateF,
                (price, tax) -> System.out.printf("Logged: price=%.2f, rate=%.0f%%%n", price, tax * 100));
        logged.get();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. allOf and anyOf
    // -------------------------------------------------------------------------
    static void allOfAndAnyOf() throws Exception {
        System.out.println("--- 5. allOf and anyOf ---");

        List<CompletableFuture<String>> tasks = Arrays.asList(
                CompletableFuture.supplyAsync(() -> { simulateWork(100); return "Task-A done"; }, pool),
                CompletableFuture.supplyAsync(() -> { simulateWork(50);  return "Task-B done"; }, pool),
                CompletableFuture.supplyAsync(() -> { simulateWork(150); return "Task-C done"; }, pool)
        );

        // allOf — waits for ALL futures to complete (returns Void)
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                tasks.toArray(new CompletableFuture[0]));

        // After allOf, collect results (all are now complete — .join() won't block)
        allDone.thenRun(() -> {
            List<String> results = tasks.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
            System.out.println("allOf results: " + results);
        }).get();

        // anyOf — returns as soon as the FIRST future completes
        CompletableFuture<Object> first = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> { simulateWork(200); return "slow"; }, pool),
                CompletableFuture.supplyAsync(() -> { simulateWork(20);  return "fast"; }, pool),
                CompletableFuture.supplyAsync(() -> { simulateWork(100); return "medium"; }, pool)
        );
        System.out.println("anyOf winner: " + first.get());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Error Handling
    // -------------------------------------------------------------------------
    static void errorHandling() throws Exception {
        System.out.println("--- 6. Error Handling ---");

        // exceptionally — provide a fallback value when the future fails
        CompletableFuture<String> withFallback = CompletableFuture
                .supplyAsync(() -> { throw new RuntimeException("Service down"); })
                .exceptionally(ex -> "fallback-value (error: " + ex.getMessage() + ")");
        System.out.println("exceptionally: " + withFallback.get());

        // handle — runs on success OR failure; can inspect both result and exception
        CompletableFuture<String> handled = CompletableFuture
                .supplyAsync(() -> { throw new RuntimeException("DB error"); })
                .handle((result, ex) -> {
                    if (ex != null) return "Handled error: " + ex.getCause().getMessage();
                    return "Success: " + result;
                });
        System.out.println("handle (failure): " + handled.get());

        CompletableFuture<String> handledOk = CompletableFuture
                .supplyAsync(() -> "ok-value")
                .handle((result, ex) -> {
                    if (ex != null) return "Handled error: " + ex.getMessage();
                    return "Success: " + result;
                });
        System.out.println("handle (success): " + handledOk.get());

        // whenComplete — side effect on both success and failure (no transformation)
        CompletableFuture.supplyAsync(() -> "completed-value")
                .whenComplete((result, ex) -> {
                    if (ex != null) System.out.println("whenComplete ERROR: " + ex.getMessage());
                    else System.out.println("whenComplete OK: " + result);
                }).get();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Real-World: E-Commerce Product Page (parallel data fetching)
    // -------------------------------------------------------------------------
    static void realWorldProductPage() throws Exception {
        System.out.println("--- 7. Real-World: E-Commerce Product Page ---");

        long productId = 101L;

        // Fetch product details, price, and inventory in PARALLEL — not sequentially
        CompletableFuture<String>  detailsF   = CompletableFuture.supplyAsync(
                () -> fetchProductDetails(productId), pool);
        CompletableFuture<Double>  priceF     = CompletableFuture.supplyAsync(
                () -> fetchProductPrice(productId), pool);
        CompletableFuture<Integer> inventoryF = CompletableFuture.supplyAsync(
                () -> fetchInventory(productId), pool);

        // Combine all three when ready
        CompletableFuture<Void> pageReady = CompletableFuture.allOf(detailsF, priceF, inventoryF);

        long start = System.currentTimeMillis();
        pageReady.thenRun(() -> {
            String details  = detailsF.join();
            double price    = priceF.join();
            int    stock    = inventoryF.join();

            System.out.printf("Product: %s%n", details);
            System.out.printf("Price:   £%.2f%n", price);
            System.out.printf("Stock:   %s%n", stock > 0 ? stock + " in stock" : "Out of stock");
        }).get();
        System.out.printf("Page loaded in %d ms (would be 3x slower sequentially)%n",
                System.currentTimeMillis() - start);

        // With error handling — graceful degradation
        CompletableFuture<ProductPageResult> robustPage = CompletableFuture
                .supplyAsync(() -> fetchProductDetails(productId), pool)
                .thenCombine(
                        CompletableFuture.supplyAsync(() -> fetchProductPrice(productId), pool)
                                .exceptionally(ex -> -1.0),           // price failure → -1
                        (details, price) -> new ProductPageResult(details, price))
                .thenCombine(
                        CompletableFuture.supplyAsync(() -> fetchInventory(productId), pool)
                                .exceptionally(ex -> 0),              // inventory failure → 0
                        (result, stock) -> { result.stock = stock; return result; });

        ProductPageResult page = robustPage.get();
        System.out.println("Robust page: " + page);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 8. Parallel Employee Processing
    // -------------------------------------------------------------------------
    static void parallelEmployeeProcessing() throws Exception {
        System.out.println("--- 8. Parallel Employee Processing ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Process each employee asynchronously (e.g. fetch external HR data)
        List<CompletableFuture<String>> futures = employees.stream()
                .filter(Employee::isActive)
                .map(emp -> CompletableFuture.supplyAsync(
                        () -> enrichEmployee(emp), pool))
                .collect(Collectors.toList());

        // Wait for all, collect results
        CompletableFuture<List<String>> allResults = CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));

        List<String> enriched = allResults.get(5, TimeUnit.SECONDS);
        enriched.stream().limit(4).forEach(s -> System.out.println("  " + s));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Simulated service calls (add artificial latency to model real I/O)
    // -------------------------------------------------------------------------

    static void simulateWork(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static long fetchUserId(String username) {
        simulateWork(30);
        return username.hashCode() & 0xFFFFFFFFL;
    }

    static CompletableFuture<String> fetchUserName(long id, Executor ex) {
        return CompletableFuture.supplyAsync(() -> {
            simulateWork(30);
            return "User#" + id;
        }, ex);
    }

    static CompletableFuture<String> fetchGreeting(String name, Executor ex) {
        return CompletableFuture.supplyAsync(() -> {
            simulateWork(20);
            return "Hello, " + name + "!";
        }, ex);
    }

    static String fetchProductDetails(long id) {
        simulateWork(80);
        return "Laptop Pro (id=" + id + ")";
    }

    static double fetchProductPrice(long id) {
        simulateWork(60);
        return 1299.99;
    }

    static int fetchInventory(long id) {
        simulateWork(70);
        return 42;
    }

    static String enrichEmployee(Employee emp) {
        simulateWork(20);  // simulate external API call
        return String.format("%-15s dept=%-12s salary=£%.0f  [enriched on %s]",
                emp.getName(), emp.getDepartment(), emp.getSalary(),
                Thread.currentThread().getName());
    }

    static class ProductPageResult {
        String details;
        double price;
        int stock;

        ProductPageResult(String details, double price) {
            this.details = details;
            this.price = price;
        }

        @Override public String toString() {
            return String.format("ProductPage{details='%s', price=%.2f, stock=%d}", details, price, stock);
        }
    }
}

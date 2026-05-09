package com.devopsmonk.java8.ch08_parallel;

import com.devopsmonk.java8.model.Employee;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * Chapter 8 — Parallel Streams
 * Tutorial: https://devops-monk.com/tutorials/java8/parallel-streams/
 *
 * Covers:
 *  - How parallel streams use ForkJoinPool.commonPool()
 *  - When parallelism actually helps (and when it hurts)
 *  - Thread-safety requirements
 *  - Ordering guarantees
 *  - Custom ForkJoinPool for parallel streams
 *  - Benchmarking: don't guess, measure
 */
public class ParallelStreamExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 8: Parallel Streams ===\n");

        basicParallelStream();
        whenParallelHelps();
        whenParallelHurts();
        orderingBehaviour();
        threadSafetyPitfall();
        customForkJoinPool();
        parallelStreamBestPractices();
    }

    // -------------------------------------------------------------------------
    // 1. Basic Parallel Stream
    // -------------------------------------------------------------------------
    static void basicParallelStream() {
        System.out.println("--- 1. Basic Parallel Stream ---");

        List<Employee> employees = Employee.SampleData.employees();

        // Sequential
        double seqSum = employees.stream()
                .mapToDouble(Employee::getSalary)
                .sum();

        // Parallel — same result, potentially faster on large data
        double parSum = employees.parallelStream()
                .mapToDouble(Employee::getSalary)
                .sum();

        System.out.printf("Sequential sum: £%.0f%n", seqSum);
        System.out.printf("Parallel sum:   £%.0f  (same result)%n", parSum);

        // Check if a stream is parallel
        System.out.println("Is parallel: " + employees.parallelStream().isParallel());

        // Convert between sequential and parallel
        Stream<Employee> seq = employees.parallelStream().sequential();
        System.out.println("After .sequential(): " + seq.isParallel());

        // Which threads are used?
        System.out.println("\nThreads used by parallel stream:");
        employees.parallelStream()
                 .map(e -> Thread.currentThread().getName())
                 .distinct()
                 .forEach(t -> System.out.println("  " + t));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. When Parallel Streams Help — CPU-bound work on large datasets
    // -------------------------------------------------------------------------
    static void whenParallelHelps() throws InterruptedException {
        System.out.println("--- 2. When Parallel Helps ---");

        int N = 10_000_000;
        List<Double> bigList = new ArrayList<>(N);
        Random rnd = new Random(42);
        for (int i = 0; i < N; i++) bigList.add(rnd.nextDouble());

        // Sequential sum
        long t0 = System.currentTimeMillis();
        double seqResult = bigList.stream().mapToDouble(Double::doubleValue).sum();
        long seqMs = System.currentTimeMillis() - t0;

        // Parallel sum
        t0 = System.currentTimeMillis();
        double parResult = bigList.parallelStream().mapToDouble(Double::doubleValue).sum();
        long parMs = System.currentTimeMillis() - t0;

        System.out.printf("Sequential: %.4f in %d ms%n", seqResult, seqMs);
        System.out.printf("Parallel:   %.4f in %d ms%n", parResult, parMs);
        System.out.println("Parallel is fastest when N is large and work per element is CPU-bound.");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. When Parallel Streams Hurt
    // -------------------------------------------------------------------------
    static void whenParallelHurts() {
        System.out.println("--- 3. When Parallel Hurts ---");

        // Small data — parallelism overhead dominates
        List<Integer> small = Arrays.asList(1, 2, 3, 4, 5);

        long t0 = System.nanoTime();
        int seqSum = small.stream().mapToInt(Integer::intValue).sum();
        long seqNs = System.nanoTime() - t0;

        t0 = System.nanoTime();
        int parSum = small.parallelStream().mapToInt(Integer::intValue).sum();
        long parNs = System.nanoTime() - t0;

        System.out.printf("Small list sequential: %d ns%n", seqNs);
        System.out.printf("Small list parallel:   %d ns  (often slower due to overhead)%n", parNs);

        // I/O-bound work — parallel doesn't help (threads block waiting for I/O)
        // Simulation: sleep represents I/O latency
        System.out.println("I/O-bound work: parallel threads all block on I/O — no speedup, more overhead.");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Ordering Behaviour — parallel streams can reorder
    // -------------------------------------------------------------------------
    static void orderingBehaviour() {
        System.out.println("--- 4. Ordering in Parallel Streams ---");

        List<Integer> ordered = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // forEach — NOT guaranteed to be in order in parallel
        System.out.print("parallel forEach (may be out of order): ");
        ordered.parallelStream().forEach(n -> System.out.print(n + " "));
        System.out.println();

        // forEachOrdered — guarantees encounter order (at cost of some parallelism)
        System.out.print("parallel forEachOrdered (always ordered): ");
        ordered.parallelStream().forEachOrdered(n -> System.out.print(n + " "));
        System.out.println();

        // collect() preserves order even in parallel (for ordered sources)
        List<Integer> result = ordered.parallelStream()
                .filter(n -> n % 2 == 0)
                .collect(Collectors.toList());
        System.out.println("parallel collect() is ordered: " + result);

        // unordered() hint — allows better parallelism when order doesn't matter
        long count = ordered.parallelStream()
                .unordered()
                .filter(n -> n > 5)
                .count();
        System.out.println("Count >5 (unordered parallel): " + count);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Thread Safety Pitfall — never mutate shared state in parallel streams
    // -------------------------------------------------------------------------
    static void threadSafetyPitfall() {
        System.out.println("--- 5. Thread-Safety Pitfall ---");

        List<Integer> numbers = IntStream.rangeClosed(1, 1000).boxed().collect(Collectors.toList());

        // WRONG: mutating a non-thread-safe list in parallel — race condition
        List<Integer> unsafeResult = new ArrayList<>();
        numbers.parallelStream().forEach(unsafeResult::add);  // BUG: ArrayList is not thread-safe
        System.out.println("Unsafe result size (should be 1000, may not be): " + unsafeResult.size());

        // CORRECT: use collect() — it uses a thread-local accumulator per thread
        List<Integer> safeResult = numbers.parallelStream().collect(Collectors.toList());
        System.out.println("Safe result size:   " + safeResult.size());

        // CORRECT: use a thread-safe collection
        List<Integer> concurrentResult = new CopyOnWriteArrayList<>();
        numbers.parallelStream().forEach(concurrentResult::add);
        System.out.println("CopyOnWrite size:   " + concurrentResult.size());

        // Rule: NEVER mutate shared mutable state inside a parallel stream.
        // Use collect(), reduce(), or thread-safe structures instead.
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Custom ForkJoinPool — control parallelism level
    // -------------------------------------------------------------------------
    static void customForkJoinPool() throws Exception {
        System.out.println("--- 6. Custom ForkJoinPool ---");

        // By default, parallelStream() uses ForkJoinPool.commonPool()
        // which has (CPU cores - 1) threads.
        System.out.println("Common pool parallelism: " + ForkJoinPool.commonPool().getParallelism());

        // To control parallelism, submit the stream task to a custom pool
        ForkJoinPool customPool = new ForkJoinPool(2);  // 2 threads

        List<Integer> data = IntStream.rangeClosed(1, 100).boxed().collect(Collectors.toList());

        ForkJoinTask<Integer> task = customPool.submit(() ->
                data.parallelStream().mapToInt(Integer::intValue).sum()
        );

        int result = task.get();
        System.out.println("Sum via custom pool (2 threads): " + result);
        customPool.shutdown();

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Best Practices Summary
    // -------------------------------------------------------------------------
    static void parallelStreamBestPractices() {
        System.out.println("--- 7. Parallel Stream Best Practices ---");

        System.out.println(
            "✓ Use parallel streams when:\n" +
            "  - N > ~10,000 elements\n" +
            "  - Work per element is CPU-bound (not I/O)\n" +
            "  - Operations are stateless and order-independent\n" +
            "  - You have measured that sequential is too slow\n\n" +
            "✗ Do NOT use parallel streams when:\n" +
            "  - Data is small (overhead exceeds gain)\n" +
            "  - Operations involve I/O, locking, or sleeping\n" +
            "  - You mutate shared state (ArrayList, HashMap, etc.)\n" +
            "  - Source is an ordered stream and order matters\n" +
            "  - You haven't benchmarked — never assume parallel is faster"
        );

        // Example: a case where parallel is clearly appropriate
        long N = 50_000_000L;
        long sum = LongStream.rangeClosed(1, N).parallel().sum();
        System.out.println("\nSum of 1 to " + N + ": " + sum);
    }
}

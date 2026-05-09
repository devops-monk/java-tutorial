package com.devopsmonk.java8.ch14_newapis;

import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;

/**
 * Chapter 14 — New APIs: Base64, StampedLock, LongAdder
 * Tutorial: https://devops-monk.com/tutorials/java8/new-apis/
 *
 * Covers:
 *  - java.util.Base64 — built-in encoder/decoder (basic, URL-safe, MIME)
 *  - StampedLock — optimistic reads for high-throughput read-heavy concurrency
 *  - LongAdder / LongAccumulator — faster concurrent counters than AtomicLong
 *  - Arrays.parallelSort — parallel sorting of large arrays
 *  - Integer.compareUnsigned, Math.addExact — unsigned and overflow-safe arithmetic
 */
public class NewApisExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 14: New APIs ===\n");

        base64Examples();
        stampedLockExample();
        longAdderExample();
        parallelSortExample();
        miscArithmeticApis();
    }

    // -------------------------------------------------------------------------
    // 1. java.util.Base64 — finally built into the JDK
    //    Before Java 8: sun.misc.BASE64Encoder (internal), Apache Commons, Guava
    // -------------------------------------------------------------------------
    static void base64Examples() {
        System.out.println("--- 1. Base64 ---");

        String original = "Hello, Java 8! Special chars: +/= and spaces";

        // Basic encoder — standard Base64 alphabet (+, /, =)
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        String decoded = new String(Base64.getDecoder().decode(encoded));
        System.out.println("Original: " + original);
        System.out.println("Encoded:  " + encoded);
        System.out.println("Decoded:  " + decoded);
        System.out.println("Round-trip OK: " + original.equals(decoded));

        // URL-safe encoder — replaces + with - and / with _ (safe in URLs and file names)
        String urlEncoded = Base64.getUrlEncoder().encodeToString(original.getBytes());
        String urlDecoded = new String(Base64.getUrlDecoder().decode(urlEncoded));
        System.out.println("\nURL-safe encoded: " + urlEncoded);
        System.out.println("URL-safe decoded: " + urlDecoded);

        // withoutPadding — omit trailing '=' characters
        String noPadding = Base64.getUrlEncoder().withoutPadding().encodeToString("hello".getBytes());
        System.out.println("No padding: " + noPadding);

        // MIME encoder — wraps at 76 characters, uses CRLF line separators
        byte[] binaryData = new byte[200];
        new java.util.Random(42).nextBytes(binaryData);
        String mimeEncoded = Base64.getMimeEncoder().encodeToString(binaryData);
        System.out.println("\nMIME encoded (first 80 chars): " + mimeEncoded.substring(0, 80) + "...");

        // Real-world: encode credentials for HTTP Basic Auth header
        String credentials = "alice:s3cr3t-p@ssword";
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
        System.out.println("\nHTTP Basic Auth header: " + authHeader);

        // Decode incoming Basic Auth header
        String raw = authHeader.substring("Basic ".length());
        String[] parts = new String(Base64.getDecoder().decode(raw)).split(":", 2);
        System.out.println("Username: " + parts[0] + " | Password: " + parts[1]);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. StampedLock — optimistic reads for read-heavy workloads
    //
    // ReentrantReadWriteLock allows concurrent reads but still serialises them
    // through the lock acquisition itself. StampedLock adds optimistic reads:
    // try to read without acquiring the lock at all; validate afterward.
    // If the validation fails (a write happened concurrently), fall back to a
    // proper read lock. In read-heavy workloads, this avoids lock contention
    // entirely on the happy path.
    // -------------------------------------------------------------------------
    static void stampedLockExample() throws InterruptedException {
        System.out.println("--- 2. StampedLock ---");

        Point point = new Point();

        ExecutorService exec = Executors.newFixedThreadPool(6);

        // Start 4 reader threads and 2 writer threads
        for (int i = 0; i < 4; i++) {
            exec.submit(() -> {
                for (int r = 0; r < 5; r++) {
                    double dist = point.distanceFromOrigin();
                    // System.out.printf("  [reader] distance=%.2f%n", dist);
                }
            });
        }
        for (int i = 0; i < 2; i++) {
            final int wi = i;
            exec.submit(() -> {
                for (int w = 0; w < 3; w++) {
                    point.move(wi * 1.5, wi * 2.0);
                    // System.out.printf("  [writer] moved%n");
                }
            });
        }

        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
        System.out.printf("Final point: (%.1f, %.1f) — no data races%n", point.x, point.y);

        // When to choose StampedLock:
        // - Read >> Write ratio (e.g. 90%+ reads)
        // - Low-latency paths where lock contention is the bottleneck
        // - NOT when fairness or reentrance is needed (StampedLock is not reentrant)
        System.out.println("StampedLock optimistic read avoids lock acquisition on the happy path.");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. LongAdder / LongAccumulator — faster concurrent counters
    //
    // AtomicLong.incrementAndGet() uses a CAS loop that causes contention under
    // heavy concurrency. LongAdder maintains per-CPU-core cells and sums them
    // on .sum() — dramatically lower contention for high-frequency increments.
    // -------------------------------------------------------------------------
    static void longAdderExample() throws InterruptedException {
        System.out.println("--- 3. LongAdder vs AtomicLong ---");

        int threads = 8;
        int increments = 1_000_000;

        // AtomicLong benchmark
        java.util.concurrent.atomic.AtomicLong atomicLong = new java.util.concurrent.atomic.AtomicLong();
        long t0 = System.nanoTime();
        runConcurrent(threads, () -> {
            for (int i = 0; i < increments; i++) atomicLong.incrementAndGet();
        });
        long atomicMs = (System.nanoTime() - t0) / 1_000_000;

        // LongAdder benchmark
        LongAdder adder = new LongAdder();
        t0 = System.nanoTime();
        runConcurrent(threads, () -> {
            for (int i = 0; i < increments; i++) adder.increment();
        });
        long adderMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("AtomicLong: %,d ms | final=%d%n", atomicMs, atomicLong.get());
        System.out.printf("LongAdder:  %,d ms | final=%d%n", adderMs, adder.sum());
        System.out.println("LongAdder is typically 2-5x faster under high contention.");

        // Real-world: page view counter, event counter, request rate counter
        LongAdder requestCounter = new LongAdder();
        LongAdder errorCounter   = new LongAdder();

        // Simulate incoming requests
        for (int i = 0; i < 1000; i++) {
            requestCounter.increment();
            if (i % 50 == 0) errorCounter.increment();
        }
        System.out.printf("Requests: %d | Errors: %d | Error rate: %.1f%%%n",
                requestCounter.sum(), errorCounter.sum(),
                100.0 * errorCounter.sum() / requestCounter.sum());

        // LongAccumulator — generalised version for any binary operation
        LongAccumulator maxSalary = new LongAccumulator(Long::max, Long.MIN_VALUE);
        java.util.Arrays.asList(95000L, 88000L, 115000L, 82000L).parallelStream()
                .forEach(maxSalary::accumulate);
        System.out.println("Max salary (LongAccumulator): £" + maxSalary.get());

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Arrays.parallelSort — parallel merge sort for large arrays
    // -------------------------------------------------------------------------
    static void parallelSortExample() {
        System.out.println("--- 4. Arrays.parallelSort ---");

        int N = 5_000_000;
        int[] dataSeq = new int[N];
        int[] dataPar = new int[N];
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < N; i++) {
            dataSeq[i] = dataPar[i] = rnd.nextInt();
        }

        long t0 = System.currentTimeMillis();
        java.util.Arrays.sort(dataSeq);
        long seqMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        java.util.Arrays.parallelSort(dataPar);
        long parMs = System.currentTimeMillis() - t0;

        System.out.printf("Arrays.sort:         %d ms (N=%,d)%n", seqMs, N);
        System.out.printf("Arrays.parallelSort: %d ms (N=%,d)%n", parMs, N);
        System.out.println("parallelSort uses ForkJoin internally; fastest for N > ~100,000.");

        // Also works with Objects and Comparators
        String[] names = {"Charlie", "Alice", "Bob", "Diana", "Eve"};
        java.util.Arrays.parallelSort(names, String::compareTo);
        System.out.println("Parallel-sorted names: " + java.util.Arrays.toString(names));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Miscellaneous: unsigned arithmetic, overflow-safe math
    // -------------------------------------------------------------------------
    static void miscArithmeticApis() {
        System.out.println("--- 5. Misc Arithmetic APIs ---");

        // Integer/Long: unsigned operations
        // In Java, int is signed. 0xFFFFFFFF is -1 as signed, 4294967295 as unsigned.
        int a = 0xFFFFFFFF;  // -1 signed
        int b = 1;
        System.out.println("Signed:   a=" + a + "  b=" + b);
        System.out.println("compareUnsigned(a, b): " + Integer.compareUnsigned(a, b));  // a > b unsigned
        System.out.println("toUnsignedLong(a): " + Integer.toUnsignedLong(a));          // 4294967295
        System.out.println("toUnsignedString(a): " + Integer.toUnsignedString(a));      // "4294967295"

        // Math.addExact / subtractExact / multiplyExact — throw ArithmeticException on overflow
        // Instead of silently wrapping around (the old Java behaviour)
        try {
            int result = Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("Math.addExact overflow: " + e.getMessage());
        }

        System.out.println("Math.addExact(100, 200): " + Math.addExact(100, 200));
        System.out.println("Math.floorDiv(7, 2):  " + Math.floorDiv(7, 2));     // 3
        System.out.println("Math.floorDiv(-7, 2): " + Math.floorDiv(-7, 2));    // -4 (floor, not truncate)
        System.out.println("Math.floorMod(7, 3):  " + Math.floorMod(7, 3));     // 1
        System.out.println("Math.floorMod(-1, 3): " + Math.floorMod(-1, 3));    // 2 (always non-negative when divisor>0)
    }

    // =========================================================================
    // Helper classes
    // =========================================================================

    static class Point {
        private double x, y;
        private final StampedLock lock = new StampedLock();

        void move(double dx, double dy) {
            long stamp = lock.writeLock();
            try { x += dx; y += dy; }
            finally { lock.unlockWrite(stamp); }
        }

        double distanceFromOrigin() {
            // 1. Try optimistic read — no lock acquired
            long stamp = lock.tryOptimisticRead();
            double curX = x, curY = y;

            // 2. Validate: did a writer change x/y between our read and now?
            if (!lock.validate(stamp)) {
                // 3. Optimistic read failed — fall back to a proper read lock
                stamp = lock.readLock();
                try { curX = x; curY = y; }
                finally { lock.unlockRead(stamp); }
            }
            return Math.sqrt(curX * curX + curY * curY);
        }
    }

    static void runConcurrent(int threads, Runnable task) throws InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) exec.submit(task);
        exec.shutdown();
        exec.awaitTermination(30, TimeUnit.SECONDS);
    }
}

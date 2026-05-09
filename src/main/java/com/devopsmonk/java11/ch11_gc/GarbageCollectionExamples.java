package com.devopsmonk.java11.ch11_gc;

import java.lang.management.*;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Chapter 11 — Garbage Collection: G1GC, ZGC, Epsilon, AppCDS
 * Tutorial: https://devops-monk.com/tutorials/java11/garbage-collection/
 *
 * Java 11 GC changes:
 *   G1GC    — default GC since Java 9 (replaced Parallel GC as default)
 *   ZGC     — experimental in Java 11, production-ready in Java 15 (< 1ms pauses)
 *   Epsilon  — no-op GC for benchmarking and testing (JEP 318, Java 11)
 *   AppCDS  — Application Class Data Sharing for faster startup (JEP 310)
 *
 * GC log format changed in Java 9 (Xlog replaces PrintGCDetails).
 */
public class GarbageCollectionExamples {

    public static void main(String[] args) {
        System.out.println("=== Ch 11: Garbage Collection ===\n");

        gcOverview();
        monitorGcViaMxBeans();
        memoryPoolAnalysis();
        gcLogMigration();
        g1gcTuning();
        zgcGuide();
        epsilonGcGuide();
        appCdsGuide();
        containerAwareJvm();
    }

    // -------------------------------------------------------------------------
    // 1. GC Overview — which GC to choose
    // -------------------------------------------------------------------------
    static void gcOverview() {
        System.out.println("--- 1. GC Selection Guide ---");

        System.out.println(
            "  GC          Default?  Java    Latency    Throughput  Use case\n" +
            "  ─────────────────────────────────────────────────────────────────\n" +
            "  G1GC        YES       9–21    Low        High        General purpose\n" +
            "  ParallelGC  (8 dflt)  8–21    Medium     Highest     Batch jobs, ETL\n" +
            "  ZGC         No        11+     <1ms       High        Latency-critical\n" +
            "  ShenandoahGC No       12+     <10ms      High        Low-pause alternative\n" +
            "  SerialGC    No        all     High       Low         Single-CPU, small heap\n" +
            "  EpsilonGC   No        11      N/A (OOMGE)N/A         Benchmarks only\n\n" +
            "  Enable with:\n" +
            "    -XX:+UseG1GC          (default in Java 9+, explicit is fine)\n" +
            "    -XX:+UseZGC           (Java 11 experimental, Java 15 production)\n" +
            "    -XX:+UseShenandoahGC  (Java 12+, OpenJDK/Red Hat builds)\n" +
            "    -XX:+UseEpsilonGC     (Java 11, requires -XX:+UnlockExperimentalVMOptions)\n"
        );
    }

    // -------------------------------------------------------------------------
    // 2. Monitor GC via MXBeans — measure GC pauses in your own application
    // -------------------------------------------------------------------------
    static void monitorGcViaMxBeans() {
        System.out.println("--- 2. Monitor GC via MXBeans ---");

        // List all GC collectors active in this JVM
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        System.out.println("  Active GC collectors:");
        for (GarbageCollectorMXBean gc : gcBeans) {
            System.out.printf("    %-30s  collections=%-5d  time=%d ms%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }

        // Trigger some GC work and measure the delta
        long gcTimeBefore = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
        long gcCountBefore = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();

        // Allocate ~50MB of short-lived objects to pressure GC
        System.out.println("\n  Allocating short-lived objects to trigger GC...");
        for (int i = 0; i < 500; i++) {
            byte[] trash = new byte[100_000];  // 100KB, immediately unreferenced
            Arrays.fill(trash, (byte) i);
        }
        System.gc();  // hint — not guaranteed, but usually works in demos

        long gcTimeAfter  = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
        long gcCountAfter = gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();

        System.out.printf("  GC collections since start: %d  (added: %d)%n",
                gcCountAfter, gcCountAfter - gcCountBefore);
        System.out.printf("  GC time since start:        %d ms  (added: %d ms)%n",
                gcTimeAfter, gcTimeAfter - gcTimeBefore);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Memory Pool Analysis
    // -------------------------------------------------------------------------
    static void memoryPoolAnalysis() {
        System.out.println("--- 3. Memory Pool Analysis ---");

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        System.out.printf("  %-30s %-8s %8s %8s %8s%n", "Pool", "Type", "Used MB", "Comm MB", "Max MB");
        System.out.println("  " + "-".repeat(70));

        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            long usedMB  = usage.getUsed()      / (1024 * 1024);
            long commMB  = usage.getCommitted()  / (1024 * 1024);
            long maxBytes = usage.getMax();
            String maxStr = maxBytes < 0 ? "unlimited" : String.valueOf(maxBytes / (1024 * 1024));
            System.out.printf("  %-30s %-8s %8d %8d %8s%n",
                    pool.getName(),
                    pool.getType().toString().substring(0, 4),
                    usedMB, commMB, maxStr);
        }

        // Overall heap
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        System.out.printf("%n  Heap:  used=%d MB  committed=%d MB  max=%d MB%n",
                heap.getUsed() / (1024 * 1024),
                heap.getCommitted() / (1024 * 1024),
                heap.getMax() / (1024 * 1024));

        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();
        System.out.printf("  Non-heap (Metaspace etc): used=%d MB%n",
                nonHeap.getUsed() / (1024 * 1024));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. GC Log Migration — Java 8 flags → Java 11 Xlog format
    // -------------------------------------------------------------------------
    static void gcLogMigration() {
        System.out.println("--- 4. GC Log Migration (Java 8 → Java 11) ---");

        System.out.println(
            "  Java 8 flags (removed in Java 9+):\n" +
            "    -XX:+PrintGCDetails\n" +
            "    -XX:+PrintGCDateStamps\n" +
            "    -Xloggc:gc.log\n" +
            "    -XX:+UseGCLogFileRotation\n" +
            "    -XX:NumberOfGCLogFiles=5\n" +
            "    -XX:GCLogFileSize=20m\n\n" +
            "  Java 11 Xlog equivalent:\n" +
            "    -Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=20m\n\n" +
            "  Common Xlog selectors:\n" +
            "    -Xlog:gc                 — basic GC events only\n" +
            "    -Xlog:gc*                — all GC logging (verbose)\n" +
            "    -Xlog:gc+heap=debug      — heap size at each GC\n" +
            "    -Xlog:safepoint          — safepoint events\n" +
            "    -Xlog:gc*,safepoint:file=jvm.log:time,level\n\n" +
            "  Java 11 GC log format:\n" +
            "    [2024-01-15T09:01:00.123+0000][info][gc] GC(42) Pause Young (Normal) 128M->64M(512M) 12.345ms\n"
        );
    }

    // -------------------------------------------------------------------------
    // 5. G1GC Tuning
    // -------------------------------------------------------------------------
    static void g1gcTuning() {
        System.out.println("--- 5. G1GC Tuning Guide ---");

        System.out.println(
            "  G1GC is the default since Java 9. Most apps need NO tuning — just:\n" +
            "    -Xms=<heap>  -Xmx=<heap>  (set equal to avoid heap resizing)\n\n" +
            "  When to tune G1GC:\n" +
            "    Problem: long GC pauses (> 500ms)\n" +
            "    Fix:     -XX:MaxGCPauseMillis=200  (default 200ms, lower = more GC overhead)\n\n" +
            "    Problem: frequent Full GC (humongous allocations)\n" +
            "    Fix:     -XX:G1HeapRegionSize=16m  (increase region size, default: 1–32MB based on heap)\n\n" +
            "    Problem: high allocation rate causing young GC every few seconds\n" +
            "    Fix:     increase -Xmx; profile allocation hotspots with JFR\n\n" +
            "    Problem: old gen too full (concurrent GC can't keep up)\n" +
            "    Fix:     -XX:InitiatingHeapOccupancyPercent=35  (start concurrent GC earlier, default 45)\n\n" +
            "  G1GC flags to know:\n" +
            "    -XX:+UseG1GC                       — enable (default in Java 9+)\n" +
            "    -XX:MaxGCPauseMillis=200            — pause target (best-effort)\n" +
            "    -XX:G1HeapRegionSize=N              — region size (1–32MB, power of 2)\n" +
            "    -XX:G1NewSizePercent=5              — minimum young gen size (% of heap)\n" +
            "    -XX:G1MaxNewSizePercent=60          — maximum young gen size\n" +
            "    -XX:+UseStringDeduplication         — deduplicate identical Strings (saves memory)\n"
        );
    }

    // -------------------------------------------------------------------------
    // 6. ZGC Guide
    // -------------------------------------------------------------------------
    static void zgcGuide() {
        System.out.println("--- 6. ZGC — Ultra-Low Latency GC ---");

        System.out.println(
            "  ZGC guarantees: pause times < 1ms regardless of heap size (even 16TB+)\n" +
            "  ZGC is concurrent — most work happens while your app is running.\n\n" +
            "  Enable (Java 11 — experimental, Java 15+ — production):\n" +
            "    Java 11: -XX:+UnlockExperimentalVMOptions -XX:+UseZGC\n" +
            "    Java 15: -XX:+UseZGC\n\n" +
            "  Key flags:\n" +
            "    -XX:+UseZGC\n" +
            "    -XX:ZCollectionInterval=5    — trigger ZGC every 5 seconds (proactive)\n" +
            "    -XX:ZFragmentationLimit=25   — compact when fragmentation > 25% (Java 20+)\n\n" +
            "  When to use ZGC:\n" +
            "    ✓ Latency SLA < 10ms (trading, real-time APIs)\n" +
            "    ✓ Very large heaps (> 32GB) where G1 pauses become noticeable\n" +
            "    ✗ NOT for throughput-optimised batch jobs (use ParallelGC instead)\n\n" +
            "  ZGC vs G1GC:\n" +
            "    G1GC:  ~50–200ms pauses, highest throughput, general purpose\n" +
            "    ZGC:   <1ms pauses, slightly lower throughput, latency-critical apps\n"
        );
    }

    // -------------------------------------------------------------------------
    // 7. Epsilon GC
    // -------------------------------------------------------------------------
    static void epsilonGcGuide() {
        System.out.println("--- 7. Epsilon GC — No-Op GC for Benchmarks ---");

        System.out.println(
            "  Epsilon GC does NOT collect garbage. When the heap is full: OutOfMemoryError.\n\n" +
            "  Enable:\n" +
            "    -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC\n\n" +
            "  Use cases:\n" +
            "    ✓ Microbenchmarking (eliminate GC noise from JMH results)\n" +
            "    ✓ Testing allocation rate / memory layout\n" +
            "    ✓ Short-lived CLI tools that know they won't exceed heap\n" +
            "    ✓ Verifying your app's memory footprint and allocation patterns\n\n" +
            "  ✗ NEVER use in production — guaranteed OOM on any long-running app\n"
        );
    }

    // -------------------------------------------------------------------------
    // 8. Application Class Data Sharing (AppCDS)
    // -------------------------------------------------------------------------
    static void appCdsGuide() {
        System.out.println("--- 8. AppCDS: Faster Startup ---");

        System.out.println(
            "  AppCDS creates a shared class archive that multiple JVM processes map\n" +
            "  read-only, skipping class loading and bytecode verification on startup.\n\n" +
            "  Typical startup improvement: 20–40% faster, 10–20% less memory.\n\n" +
            "  Create an archive (3 steps):\n" +
            "  1. Record which classes are loaded:\n" +
            "     java -Xshare:off -XX:DumpLoadedClassList=classes.lst -jar app.jar\n\n" +
            "  2. Create the archive:\n" +
            "     java -Xshare:dump -XX:SharedClassListFile=classes.lst \\\n" +
            "          -XX:SharedArchiveFile=app.jsa -jar app.jar\n\n" +
            "  3. Run with the archive:\n" +
            "     java -Xshare:on -XX:SharedArchiveFile=app.jsa -jar app.jar\n\n" +
            "  Dynamic AppCDS (Java 13+) — no step 1:\n" +
            "     java -XX:ArchiveClassesAtExit=app.jsa -jar app.jar  # creates archive on exit\n" +
            "     java -XX:SharedArchiveFile=app.jsa -jar app.jar     # use on next run\n\n" +
            "  Verify it's working:\n" +
            "     java -Xshare:on -verbose:class -jar app.jar 2>&1 | grep 'source: shared'\n"
        );

        // Print current JVM's CDS status
        System.out.println("  Current JVM CDS info:");
        System.out.println("    java.version: " + System.getProperty("java.version"));
        System.out.println("    java.vm.name: " + System.getProperty("java.vm.name"));
    }

    // -------------------------------------------------------------------------
    // 9. Container-Aware JVM (Java 10+)
    // -------------------------------------------------------------------------
    static void containerAwareJvm() {
        System.out.println("\n--- 9. Container-Aware JVM (Java 10+) ---");

        Runtime rt = Runtime.getRuntime();
        System.out.println("  Runtime.getRuntime().availableProcessors(): " + rt.availableProcessors());
        System.out.println("  Runtime.getRuntime().maxMemory():           " + rt.maxMemory() / (1024 * 1024) + " MB");

        System.out.println(
            "\n  Before Java 10: JVM ignored Docker --memory and --cpus limits\n" +
            "  → -Xmx defaulted to 1/4 of HOST memory, not container memory\n" +
            "  → availableProcessors() returned host CPU count, not container limit\n\n" +
            "  Java 10+: JVM reads cgroup limits automatically.\n" +
            "  Container-aware flags (enabled by default in Java 10+):\n" +
            "    -XX:+UseContainerSupport            — read cgroup memory/cpu limits (default ON)\n" +
            "    -XX:MaxRAMPercentage=75.0            — use 75% of container memory as -Xmx\n" +
            "    -XX:InitialRAMPercentage=50.0        — initial heap size\n\n" +
            "  Recommended Kubernetes / Docker config:\n" +
            "    resources:\n" +
            "      limits:\n" +
            "        memory: \"512Mi\"\n" +
            "    JVM: -XX:MaxRAMPercentage=75\n" +
            "    → Xmx ≈ 384MB  (75% of 512MB)\n\n" +
            "  Without explicit -Xmx, Java 11 will use MaxRAMPercentage of container RAM.\n"
        );
    }
}

package com.devopsmonk.java11.ch12_jfr;

import jdk.jfr.*;
import jdk.jfr.consumer.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chapter 12 — Java Flight Recorder (JEP 328): Production Profiling
 * Tutorial: https://devops-monk.com/tutorials/java11/flight-recorder/
 *
 * JFR was a commercial Oracle feature before Java 11.
 * Java 11 open-sourced it — now available in ALL OpenJDK builds.
 *
 * Key concepts:
 *   Recording   — a time window of captured events, stored in .jfr files
 *   Event       — a typed data point (GC pause, method sample, allocation, etc.)
 *   EventStream — Java 14+ streaming API (events as they happen, no file needed)
 *   JMC         — JDK Mission Control — GUI to analyse .jfr recordings
 *
 * This file demonstrates:
 *   1. Custom JFR events (@Event subclass)
 *   2. Programmatic recording via the FlightRecorder API
 *   3. Reading a .jfr file with RecordingFile
 *   4. JFR command-line cheat sheet
 */
public class FlightRecorderExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 12: Java Flight Recorder ===\n");

        customEvents();
        programmaticRecording();
        readRecordingFile();
        jfrCommandCheatSheet();
        jfrConfigurationGuide();
    }

    // -------------------------------------------------------------------------
    // 1. Custom JFR Events
    //    Extend jdk.jfr.Event — zero overhead when JFR is off
    // -------------------------------------------------------------------------

    @Name("com.devopsmonk.OrderProcessed")
    @Label("Order Processed")
    @Description("Fired whenever an order completes processing")
    @Category({"DevOpsMonk", "Business"})
    @StackTrace(false)               // skip stack trace capture to reduce overhead
    @Threshold("100 ms")             // only record if duration > 100ms
    static class OrderProcessedEvent extends Event {
        @Label("Order ID")
        long orderId;

        @Label("Customer")
        String customerId;

        @Label("Total Amount (pence)")
        long totalPence;

        @Label("Item Count")
        int itemCount;

        @Label("Payment Method")
        String paymentMethod;
    }

    @Name("com.devopsmonk.CacheOperation")
    @Label("Cache Operation")
    @Category({"DevOpsMonk", "Infrastructure"})
    static class CacheEvent extends Event {
        @Label("Cache Name")
        String cacheName;

        @Label("Operation")
        String operation;   // HIT or MISS

        @Label("Key")
        String key;
    }

    static void customEvents() throws InterruptedException {
        System.out.println("--- 1. Custom JFR Events ---");

        // Fire an OrderProcessedEvent
        var orderEvent = new OrderProcessedEvent();
        orderEvent.orderId     = 10001L;
        orderEvent.customerId  = "cust-alice-42";
        orderEvent.totalPence  = 9999;        // £99.99
        orderEvent.itemCount   = 3;
        orderEvent.paymentMethod = "CREDIT_CARD";
        orderEvent.begin();                   // start timing

        // Simulate order processing work
        Thread.sleep(50);

        orderEvent.commit();                  // end timing + record
        System.out.println("  OrderProcessedEvent fired: orderId=" + orderEvent.orderId
                + "  duration captured by JFR");

        // Fire several cache events
        String[] ops = {"MISS", "HIT", "HIT", "MISS", "HIT"};
        for (int i = 0; i < ops.length; i++) {
            var cacheEvent = new CacheEvent();
            cacheEvent.cacheName = "employee-cache";
            cacheEvent.operation = ops[i];
            cacheEvent.key       = "emp:" + (1000 + i);
            cacheEvent.commit();
        }
        System.out.println("  CacheEvents fired: " + ops.length);

        // Events with shouldCommit() — skip if event filtering would discard it
        var filteredEvent = new OrderProcessedEvent();
        filteredEvent.orderId = 10002L;
        filteredEvent.begin();
        // ... (very fast operation, will be below @Threshold)
        if (filteredEvent.shouldCommit()) {
            filteredEvent.commit();
            System.out.println("  Filtered event committed");
        } else {
            System.out.println("  Filtered event skipped (below @Threshold — expected)");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Programmatic Recording — start/stop a recording from code
    // -------------------------------------------------------------------------
    static void programmaticRecording() throws Exception {
        System.out.println("--- 2. Programmatic Recording ---");

        Path outputFile = Files.createTempFile("flight-recorder-", ".jfr");

        try (Recording recording = new Recording()) {
            recording.setName("tutorial-demo");

            // Enable specific event categories
            recording.enable("jdk.GarbageCollection").withoutThreshold();
            recording.enable("jdk.GCHeapSummary").withoutThreshold();
            recording.enable("jdk.ClassLoad").withThreshold(Duration.ofMillis(10));
            recording.enable("com.devopsmonk.OrderProcessed").withoutThreshold();
            recording.enable("com.devopsmonk.CacheOperation").withoutThreshold();

            recording.setToDisk(true);
            recording.setDestination(outputFile);
            recording.setMaxSize(64 * 1024 * 1024);   // 64 MB max file size
            recording.setMaxAge(Duration.ofMinutes(5));

            recording.start();
            System.out.println("  Recording started: " + recording.getName());

            // Simulate some application activity
            simulateApplicationWork();

            recording.stop();
            System.out.println("  Recording stopped.");
            System.out.println("  Output file: " + outputFile);
            System.out.println("  File size: " + Files.size(outputFile) + " bytes");
        }

        // Read the recording back
        readAndSummarize(outputFile);
        Files.deleteIfExists(outputFile);

        System.out.println();
    }

    static void simulateApplicationWork() throws Exception {
        // Fire a mix of our custom events
        for (int i = 0; i < 10; i++) {
            var evt = new OrderProcessedEvent();
            evt.orderId  = 20000L + i;
            evt.customerId = "cust-" + i;
            evt.totalPence = (long)(Math.random() * 50_000);
            evt.itemCount  = 1 + (int)(Math.random() * 5);
            evt.paymentMethod = i % 2 == 0 ? "CREDIT_CARD" : "PAYPAL";
            evt.begin();
            Thread.sleep(5 + (long)(Math.random() * 20));
            evt.commit();
        }

        // Trigger some allocation to get GC events
        List<byte[]> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(new byte[10_000]);
        }
        data.clear();
        System.gc();
    }

    // -------------------------------------------------------------------------
    // 3. Read a .jfr file programmatically
    // -------------------------------------------------------------------------
    static void readAndSummarize(Path file) throws IOException {
        System.out.println("  Reading .jfr file...");

        try (RecordingFile rf = new RecordingFile(file)) {
            var events = new ArrayList<RecordedEvent>();
            while (rf.hasMoreEvents()) {
                events.add(rf.readEvent());
            }

            System.out.println("  Total events recorded: " + events.size());

            // Group by event type
            var byType = events.stream()
                    .collect(Collectors.groupingBy(e -> e.getEventType().getName(),
                            Collectors.counting()));

            System.out.println("  Events by type:");
            byType.entrySet().stream()
                  .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                  .limit(10)
                  .forEach(e -> System.out.printf("    %-45s %d%n", e.getKey(), e.getValue()));

            // Summarize our custom order events
            long orderEvents = events.stream()
                    .filter(e -> e.getEventType().getName().equals("com.devopsmonk.OrderProcessed"))
                    .count();
            System.out.println("  Our OrderProcessed events: " + orderEvents);
        }
    }

    static void readRecordingFile() {
        System.out.println("--- 3. RecordingFile API ---");

        System.out.println(
            "  // Read all events from a saved .jfr file:\n" +
            "  try (RecordingFile rf = new RecordingFile(Path.of(\"recording.jfr\"))) {\n" +
            "      while (rf.hasMoreEvents()) {\n" +
            "          RecordedEvent event = rf.readEvent();\n" +
            "          String name = event.getEventType().getName();\n" +
            "          Instant start = event.getStartTime();\n" +
            "          Duration dur  = event.getDuration();\n\n" +
            "          // Access typed fields by name:\n" +
            "          if (name.equals(\"jdk.GarbageCollection\")) {\n" +
            "              String cause = event.getString(\"cause\");\n" +
            "              long gcId    = event.getLong(\"gcId\");\n" +
            "              System.out.println(\"GC \" + gcId + \" cause=\" + cause\n" +
            "                      + \" duration=\" + dur.toMillis() + \"ms\");\n" +
            "          }\n" +
            "      }\n" +
            "  }\n"
        );
    }

    // -------------------------------------------------------------------------
    // 4. JFR Command-Line Cheat Sheet
    // -------------------------------------------------------------------------
    static void jfrCommandCheatSheet() {
        System.out.println("--- 4. JFR Command-Line Cheat Sheet ---");

        System.out.println(
            "  # Start JFR at application launch:\n" +
            "  java -XX:StartFlightRecording=duration=60s,filename=app.jfr,settings=profile -jar app.jar\n\n" +
            "  # Enable always-on JFR with low overhead (production default):\n" +
            "  java -XX:StartFlightRecording=settings=default,disk=true,maxage=2h,maxsize=256m,\\\n" +
            "       name=continuous -jar app.jar\n\n" +
            "  # Attach to a running JVM and take a 60-second snapshot:\n" +
            "  jcmd <pid> JFR.start duration=60s filename=snapshot.jfr\n" +
            "  jcmd <pid> JFR.dump filename=now.jfr\n" +
            "  jcmd <pid> JFR.stop\n\n" +
            "  # List active recordings:\n" +
            "  jcmd <pid> JFR.check\n\n" +
            "  # Settings profiles:\n" +
            "    default — low overhead (~1%), suitable for production always-on\n" +
            "    profile — higher detail (~2%), for short profiling sessions\n\n" +
            "  # Analyse with JDK Mission Control (JMC):\n" +
            "  Download from: https://jdk.java.net/jmc/\n" +
            "  Open .jfr file → see flame graphs, allocation analysis, GC analysis\n"
        );
    }

    // -------------------------------------------------------------------------
    // 5. JFR Configuration Guide
    // -------------------------------------------------------------------------
    static void jfrConfigurationGuide() {
        System.out.println("--- 5. JFR Production Configuration ---");

        System.out.println(
            "  Recommended always-on production JFR setup:\n\n" +
            "  JVM flags:\n" +
            "    -XX:StartFlightRecording=\\\n" +
            "      settings=default,\\\n" +
            "      disk=true,\\\n" +
            "      maxage=1h,\\\n" +
            "      maxsize=256m,\\\n" +
            "      name=continuous,\\\n" +
            "      path-to-gc-roots=false\n\n" +
            "  When an incident occurs:\n" +
            "    jcmd <pid> JFR.dump filename=/var/log/incident-$(date +%s).jfr\n\n" +
            "  What JFR captures in 'default' profile:\n" +
            "    ✓ GC pauses, GC heap summary\n" +
            "    ✓ Method profiling (sampled, not instrumented)\n" +
            "    ✓ Thread allocation rate\n" +
            "    ✓ Monitor contention (threads blocked on synchronized)\n" +
            "    ✓ Class loading and unloading\n" +
            "    ✓ JIT compilation events\n" +
            "    ✓ File and socket I/O (slow operations)\n" +
            "    ✓ Exception events (if -XX:+FlightRecorderUnlockedCommands)\n" +
            "    ✓ Your custom @Event subclasses (automatically included)\n"
        );
    }
}

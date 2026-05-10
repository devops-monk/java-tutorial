package com.devopsmonk.java17.ch10_deserialization_filters;

import java.io.*;
import java.io.ObjectInputFilter.Config;
import java.io.ObjectInputFilter.FilterInfo;
import java.io.ObjectInputFilter.Status;
import java.util.List;

/**
 * Java 17 — Context-Specific Deserialization Filters (JEP 415)
 * Matches blog article: 10-deserialization-filters.md
 */
public class DeserializationFilterExamples {

    // ── Serialisable models ───────────────────────────────────────────────────
    static class UserPreferences implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        String theme;
        boolean darkMode;
        int fontSize;

        UserPreferences(String theme, boolean darkMode, int fontSize) {
            this.theme = theme;
            this.darkMode = darkMode;
            this.fontSize = fontSize;
        }

        @Override public String toString() {
            return "UserPreferences{theme=" + theme + ", darkMode=" + darkMode
                 + ", fontSize=" + fontSize + "}";
        }
    }

    static class Order implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        String orderId;
        double amount;
        List<String> items;

        Order(String orderId, double amount, List<String> items) {
            this.orderId = orderId;
            this.amount = amount;
            this.items = items;
        }

        @Override public String toString() {
            return "Order{id=" + orderId + ", amount=" + amount + ", items=" + items + "}";
        }
    }

    // ── Helper: serialise to bytes ────────────────────────────────────────────
    static byte[] serialise(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        }
        return bos.toByteArray();
    }

    // ── Helper: deserialise with a filter ─────────────────────────────────────
    static Object deserialise(byte[] data, ObjectInputFilter filter) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bis)) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

    // ── 1. Pattern-based allowlist filter ─────────────────────────────────────
    static void patternBasedFilter() throws Exception {
        System.out.println("=== Pattern-Based Allowlist Filter ===");

        byte[] data = serialise(new UserPreferences("dark", true, 14));

        // Only allow classes in our own package — reject everything else
        ObjectInputFilter allowlist = ObjectInputFilter.Config.createFilter(
            "com.devopsmonk.java17.ch10_deserialization_filters.*" +
            ";java.util.List" +
            ";java.util.ArrayList" +
            ";!*"   // reject all other classes
        );

        try {
            UserPreferences prefs = (UserPreferences) deserialise(data, allowlist);
            System.out.println("Deserialised: " + prefs);
        } catch (InvalidClassException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        // Demonstrate rejection of unknown class
        byte[] maliciousData = serialise(new java.util.HashMap<>()); // not in allowlist
        try {
            deserialise(maliciousData, allowlist);
            System.out.println("Should not reach here");
        } catch (Exception e) {
            System.out.println("Blocked HashMap: " + e.getClass().getSimpleName());
        }
    }

    // ── 2. Custom filter — limit depth, references, and stream size ──────────
    static void customResourceLimitFilter() throws Exception {
        System.out.println("\n=== Custom Resource-Limit Filter ===");

        byte[] data = serialise(new Order("ORD-001", 99.99, List.of("Widget", "Gadget")));

        ObjectInputFilter resourceFilter = info -> {
            // Reject if nested too deep (prevents deeply nested gadget chains)
            if (info.depth() > 5)          return Status.REJECTED;
            // Reject streams that reference too many objects
            if (info.references() > 100)   return Status.REJECTED;
            // Reject very large streams (> 64KB)
            if (info.streamBytes() > 65536) return Status.REJECTED;
            // Reject arrays with more than 256 elements
            if (info.arrayLength() > 256)  return Status.REJECTED;
            return Status.ALLOWED;
        };

        Order order = (Order) deserialise(data, resourceFilter);
        System.out.println("Deserialised: " + order);
    }

    // ── 3. Filter factory — different filters per deserialization context ─────
    //
    // NOTE: setSerialFilterFactory() can only be called ONCE per JVM lifetime.
    // The built-in factory is already installed at JVM startup, so calling it
    // programmatically in a demo throws IllegalStateException.
    //
    // In production, set it via the system property at startup instead:
    //   -Djdk.serialFilterFactory=com.example.MyFilterFactory
    //
    // The equivalent composed-filter logic is demonstrated below.
    static void filterFactory() throws Exception {
        System.out.println("\n=== Filter Factory (JEP 415 core feature) ===");
        System.out.println("(setSerialFilterFactory must be set at JVM startup via");
        System.out.println(" -Djdk.serialFilterFactory=<class> or called before any");
        System.out.println(" ObjectInputStream is created. Showing composed filter instead.)");

        // ── What the factory would produce: resource-limit + allowlist composed ──
        ObjectInputFilter resourceLimit = info -> {
            if (info.depth() > 10)       return Status.REJECTED;
            if (info.references() > 500) return Status.REJECTED;
            return Status.UNDECIDED;
        };

        ObjectInputFilter allowlist = Config.createFilter(
            "com.devopsmonk.java17.ch10_deserialization_filters.*;java.util.*;!*");

        // Manually compose: resource limit is checked first, then allowlist
        ObjectInputFilter composed = info -> {
            Status s = resourceLimit.checkInput(info);
            if (s == Status.REJECTED) return Status.REJECTED;
            return allowlist.checkInput(info);
        };

        byte[] data = serialise(new UserPreferences("light", false, 12));
        UserPreferences prefs = (UserPreferences) deserialise(data, composed);
        System.out.println("Deserialised with composed (resource + allowlist) filter: " + prefs);
    }

    // ── 4. Logging filter — audit all deserialization ─────────────────────────
    static ObjectInputFilter loggingFilter(ObjectInputFilter delegate) {
        return info -> {
            Status status = (delegate != null)
                ? delegate.checkInput(info)
                : Status.UNDECIDED;

            if (info.serialClass() != null) {
                System.out.printf("  [FILTER] class=%-50s depth=%d status=%s%n",
                    info.serialClass().getName(), info.depth(), status);
            }
            return status;
        };
    }

    static void auditDeserialization() throws Exception {
        System.out.println("\n=== Audit / Logging Filter ===");

        byte[] data = serialise(new Order("ORD-002", 149.0, List.of("A", "B")));
        // [* allows array types (e.g. [Ljava.lang.Object;) used internally by List.of
        ObjectInputFilter base = Config.createFilter(
            "com.devopsmonk.java17.ch10_deserialization_filters.*;java.util.*;java.lang.*;[*;!*");

        deserialise(data, loggingFilter(base));
    }

    public static void main(String[] args) throws Exception {
        patternBasedFilter();
        customResourceLimitFilter();
        filterFactory();
        auditDeserialization();
    }
}

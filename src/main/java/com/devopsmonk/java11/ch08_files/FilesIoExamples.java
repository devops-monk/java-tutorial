package com.devopsmonk.java11.ch08_files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Chapter 08 — Files and IO Enhancements (Java 11)
 * Tutorial: https://devops-monk.com/tutorials/java11/files-io-api/
 *
 * Java 11 added convenience methods to Files and InputStream:
 *   Files.readString(Path)              — read a whole file into a String
 *   Files.writeString(Path, String)     — write a String to a file
 *   Files.mismatch(Path, Path)          — find byte position of first difference
 *   Path.of(String)                     — clean alternative to Paths.get()
 *   InputStream.readAllBytes()          — read all bytes from any stream
 *   InputStream.readNBytes(n)           — read exactly n bytes
 *   InputStream.transferTo(OutputStream)— pipe one stream to another
 */
public class FilesIoExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 08: Files and IO Enhancements ===\n");

        pathOf();
        readString();
        writeString();
        mismatch();
        inputStreamEnhancements();
        realWorldFileProcessing();
    }

    // -------------------------------------------------------------------------
    // 1. Path.of() — cleaner way to build paths (Java 11)
    // -------------------------------------------------------------------------
    static void pathOf() {
        System.out.println("--- 1. Path.of() ---");

        // Java 8: Paths.get() — works but lives in java.nio.file.Paths utility class
        Path old = Paths.get("/tmp", "data", "report.csv");

        // Java 11: Path.of() — static factory directly on Path interface
        Path modern = Path.of("/tmp", "data", "report.csv");

        System.out.println("  Paths.get: " + old);
        System.out.println("  Path.of:   " + modern);
        System.out.println("  Equal: " + old.equals(modern));

        // Single segment
        Path single = Path.of("/etc/hosts");
        System.out.println("  Single: " + single);

        // Relative path
        Path rel = Path.of("src", "main", "resources", "config.yaml");
        System.out.println("  Relative: " + rel);

        // From URI
        Path fromUri = Path.of(single.toUri());
        System.out.println("  From URI: " + fromUri);

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Files.readString() — read an entire file into a String
    // -------------------------------------------------------------------------
    static void readString() throws Exception {
        System.out.println("--- 2. Files.readString() ---");

        // Create a temp file to demo
        Path tempFile = Files.createTempFile("java11-demo-", ".txt");
        Files.writeString(tempFile,
                "Line 1: Hello from Java 11\n" +
                "Line 2: Files.readString is much simpler\n" +
                "Line 3: No BufferedReader boilerplate needed");

        // Java 8 approach (verbose):
        // StringBuilder sb = new StringBuilder();
        // try (BufferedReader br = new BufferedReader(new FileReader(tempFile.toFile()))) {
        //     String line; while ((line = br.readLine()) != null) sb.append(line).append("\n");
        // }
        // String content = sb.toString();

        // Java 11 (one line):
        String content = Files.readString(tempFile);
        System.out.println("  Content:\n    " + content.replace("\n", "\n    "));

        // With explicit charset
        String utf8Content = Files.readString(tempFile, StandardCharsets.UTF_8);
        System.out.println("  Lines: " + utf8Content.lines().count());

        // Clean up
        Files.deleteIfExists(tempFile);
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Files.writeString() — write a String to a file
    // -------------------------------------------------------------------------
    static void writeString() throws Exception {
        System.out.println("--- 3. Files.writeString() ---");

        Path output = Files.createTempFile("java11-write-", ".csv");

        // Write with default charset (UTF-8)
        Files.writeString(output, "name,department,salary\n");

        // Append using StandardOpenOption
        Files.writeString(output, "Alice,Engineering,95000\n", StandardOpenOption.APPEND);
        Files.writeString(output, "Bob,Product,82000\n",       StandardOpenOption.APPEND);
        Files.writeString(output, "Carol,Design,78000\n",      StandardOpenOption.APPEND);

        // Read it back to verify
        String csv = Files.readString(output);
        System.out.println("  Written CSV:");
        csv.lines().forEach(l -> System.out.println("    " + l));

        // Write with explicit charset
        Path utf8File = Files.createTempFile("java11-utf8-", ".txt");
        Files.writeString(utf8File, "Unicode: café, naïve, résumé", StandardCharsets.UTF_8);
        System.out.println("  UTF-8 content: " + Files.readString(utf8File, StandardCharsets.UTF_8));

        Files.deleteIfExists(output);
        Files.deleteIfExists(utf8File);
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. Files.mismatch() — find where two files first differ
    // -------------------------------------------------------------------------
    static void mismatch() throws Exception {
        System.out.println("--- 4. Files.mismatch() ---");

        Path fileA = Files.createTempFile("mismatch-a-", ".txt");
        Path fileB = Files.createTempFile("mismatch-b-", ".txt");
        Path fileC = Files.createTempFile("mismatch-c-", ".txt");

        Files.writeString(fileA, "Hello, Java 11!");
        Files.writeString(fileB, "Hello, Java 11!");   // identical to A
        Files.writeString(fileC, "Hello, Java  11!");  // extra space at pos 12

        // Returns -1 when files are identical
        long identical = Files.mismatch(fileA, fileB);
        System.out.println("  A vs B (identical): " + identical);  // -1

        // Returns byte position of first difference
        long diffPos = Files.mismatch(fileA, fileC);
        System.out.println("  A vs C (differ at byte): " + diffPos);

        // Show what's at that position
        byte[] bytesA = Files.readAllBytes(fileA);
        byte[] bytesC = Files.readAllBytes(fileC);
        if (diffPos >= 0) {
            System.out.printf("    File A byte[%d] = '%c' (0x%02X)%n", diffPos, (char)bytesA[(int)diffPos], bytesA[(int)diffPos]);
            System.out.printf("    File C byte[%d] = '%c' (0x%02X)%n", diffPos, (char)bytesC[(int)diffPos], bytesC[(int)diffPos]);
        }

        // Use case: verify file copy, detect config drift, compare deployments
        System.out.println("  Use case: detect config file drift between environments");

        Files.deleteIfExists(fileA);
        Files.deleteIfExists(fileB);
        Files.deleteIfExists(fileC);
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. InputStream enhancements — readAllBytes, readNBytes, transferTo
    // -------------------------------------------------------------------------
    static void inputStreamEnhancements() throws Exception {
        System.out.println("--- 5. InputStream Enhancements ---");

        byte[] data = "Hello, Java 11 InputStream!".getBytes(StandardCharsets.UTF_8);
        InputStream input = new ByteArrayInputStream(data);

        // readAllBytes() — reads everything (Java 11)
        // Java 8: had to use IOUtils.toByteArray(stream) from Apache Commons
        byte[] allBytes = input.readAllBytes();
        System.out.println("  readAllBytes(): " + new String(allBytes));

        // readNBytes(n) — read exactly n bytes (Java 11)
        input = new ByteArrayInputStream(data);
        byte[] firstFive = input.readNBytes(5);
        System.out.println("  readNBytes(5):  \"" + new String(firstFive) + "\"");

        // transferTo(OutputStream) — pipe one stream to another (Java 9)
        input = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long transferred = input.transferTo(out);
        System.out.println("  transferTo:     transferred " + transferred + " bytes");
        System.out.println("  Output content: " + out.toString(StandardCharsets.UTF_8));

        // Real-world: download and save a resource
        System.out.println("\n  Practical: read classpath resource without Commons IO");
        try (var stream = FilesIoExamples.class.getResourceAsStream("/java.util/Properties")) {
            if (stream != null) {
                byte[] bytes = stream.readAllBytes();
                System.out.println("  Resource size: " + bytes.length + " bytes");
            } else {
                // Simulate with inline data
                InputStream simulated = new ByteArrayInputStream(
                        "key1=value1\nkey2=value2".getBytes());
                System.out.println("  Simulated resource: " + new String(simulated.readAllBytes()));
            }
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Real-World: Process a directory of CSV reports
    // -------------------------------------------------------------------------
    static void realWorldFileProcessing() throws Exception {
        System.out.println("--- 6. Real-World: Process CSV Reports ---");

        // Create temp directory with sample CSV files
        Path dir = Files.createTempDirectory("java11-reports-");
        Path jan = dir.resolve("jan-2024.csv");
        Path feb = dir.resolve("feb-2024.csv");
        Path mar = dir.resolve("mar-2024.csv");

        Files.writeString(jan, "Alice,95000\nBob,82000\nCarol,78000\n");
        Files.writeString(feb, "Alice,95000\nBob,84000\nDave,91000\n");
        Files.writeString(mar, "Alice,97000\nCarol,80000\nDave,91000\n");

        System.out.println("  Processing " + dir.getFileName() + ":");

        // Walk directory, read each CSV, sum salaries
        try (var paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".csv"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         String content = Files.readString(p);
                         double total = content.lines()
                                 .filter(l -> !l.isBlank())
                                 .mapToDouble(l -> Double.parseDouble(l.split(",")[1].strip()))
                                 .sum();
                         System.out.printf("    %-20s total payroll: £%,.0f%n",
                                 p.getFileName(), total);
                     } catch (IOException e) {
                         System.out.println("  Error reading " + p + ": " + e.getMessage());
                     }
                 });
        }

        // Compare two months for drift
        long mismatch = Files.mismatch(jan, feb);
        System.out.println("  jan vs feb differ at byte: " + mismatch);

        // Cleanup
        Files.deleteIfExists(jan);
        Files.deleteIfExists(feb);
        Files.deleteIfExists(mar);
        Files.deleteIfExists(dir);

        System.out.println();
    }
}

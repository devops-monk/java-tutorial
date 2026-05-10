package com.devopsmonk.java17.ch09_enhanced_prng;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Java 17 — Enhanced Pseudo-Random Number Generators (JEP 356)
 * Matches blog article: 09-enhanced-prng.md
 */
public class EnhancedPrngExamples {

    // ── 1. RandomGenerator interface — algorithm-agnostic code ───────────────
    static void basicRandomGenerator() {
        // L64X128MixRandom — new LXM family, better statistical quality
        RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        System.out.println("=== RandomGenerator — L64X128MixRandom ===");
        System.out.println("nextInt(100):    " + rng.nextInt(100));
        System.out.println("nextLong():      " + rng.nextLong());
        System.out.println("nextDouble():    " + String.format("%.6f", rng.nextDouble()));
        System.out.println("nextBoolean():   " + rng.nextBoolean());
        System.out.println("nextGaussian():  " + String.format("%.4f", rng.nextGaussian()));
    }

    // ── 2. Default (legacy-compatible) generator ──────────────────────────────
    static void defaultGenerator() {
        // Equivalent to new java.util.Random()
        RandomGenerator legacy = RandomGenerator.getDefault();
        System.out.println("\n=== Default RandomGenerator ===");
        System.out.println("Algorithm: " + RandomGeneratorFactory.getDefault().name());
        System.out.println("5 random ints: " +
            legacy.ints(5, 1, 100).boxed().collect(Collectors.toList()));
    }

    // ── 3. List all available algorithms ─────────────────────────────────────
    static void listAlgorithms() {
        System.out.println("\n=== Available PRNG Algorithms ===");
        RandomGeneratorFactory.all()
            .sorted((a, b) -> a.name().compareTo(b.name()))
            .forEach(f -> System.out.printf("  %-25s  stateBits=%-4d  equidistribution=%d%n",
                f.name(), f.stateBits(), f.equidistribution()));
    }

    // ── 4. Streams of random numbers ─────────────────────────────────────────
    static void randomStreams() {
        RandomGenerator rng = RandomGenerator.of("Xoshiro256PlusPlus");

        System.out.println("\n=== Random Streams ===");

        // 5 random doubles in [0, 1)
        System.out.print("5 doubles: ");
        rng.doubles(5).forEach(d -> System.out.printf("%.3f  ", d));
        System.out.println();

        // 10 random ints in [1, 101)
        System.out.print("10 ints [1-100]: ");
        rng.ints(10, 1, 101).forEach(i -> System.out.print(i + " "));
        System.out.println();

        // Gaussian values via nextGaussian()
        System.out.print("5 Gaussian: ");
        for (int i = 0; i < 5; i++) System.out.printf("%.3f  ", rng.nextGaussian());
        System.out.println();
    }

    // ── 5. SplittableGenerator — parallel work with independent streams ───────
    static void splittableGenerator() {
        var factory = RandomGeneratorFactory.<RandomGenerator.SplittableGenerator>of("L64X256MixRandom");
        RandomGenerator.SplittableGenerator root = factory.create(42L); // seeded for reproducibility

        System.out.println("\n=== Splittable Generator — Parallel Simulation ===");
        // Each split() produces an independent generator — safe to use in parallel streams
        double avgPi = root.splits(8)
            .parallel()
            .mapToDouble(rng -> {
                long inside = 0;
                for (int i = 0; i < 100_000; i++) {
                    double x = rng.nextDouble();
                    double y = rng.nextDouble();
                    if (x * x + y * y <= 1.0) inside++;
                }
                return 4.0 * inside / 100_000;
            })
            .average()
            .orElse(0);

        System.out.printf("Monte Carlo π estimate (8 parallel workers): %.5f%n", avgPi);
    }

    // ── 6. JumpableGenerator — advance state without generating all values ────
    static void jumpableGenerator() {
        var factory = RandomGeneratorFactory.<RandomGenerator.JumpableGenerator>of("Xoroshiro128PlusPlus");
        RandomGenerator.JumpableGenerator rng = factory.create();

        System.out.println("\n=== Jumpable Generator ===");
        System.out.print("Before jump: ");
        rng.ints(3).forEach(i -> System.out.print(i + " "));
        System.out.println();

        // jump() advances state by 2^64 steps — useful for independent sub-sequences
        rng.jump();
        System.out.print("After jump:  ");
        rng.ints(3).forEach(i -> System.out.print(i + " "));
        System.out.println();
    }

    // ── 7. Practical: shuffle and sampling ───────────────────────────────────
    static void shuffleAndSample() {
        RandomGenerator rng = RandomGenerator.of("L32X64MixRandom");
        var items = List.of("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace");

        // Random sample of 3
        System.out.println("\n=== Random Sample of 3 ===");
        rng.ints(0, items.size())
           .distinct()
           .limit(3)
           .mapToObj(items::get)
           .forEach(name -> System.out.println("  " + name));

        // Simulate a dice roll (1d6) 10 times
        System.out.print("\n=== 10 Dice Rolls (1d6) ===\n  ");
        rng.ints(10, 1, 7).forEach(d -> System.out.print(d + " "));
        System.out.println();
    }

    public static void main(String[] args) {
        basicRandomGenerator();
        defaultGenerator();
        listAlgorithms();
        randomStreams();
        splittableGenerator();
        jumpableGenerator();
        shuffleAndSample();
    }
}

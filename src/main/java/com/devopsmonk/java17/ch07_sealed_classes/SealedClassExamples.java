package com.devopsmonk.java17.ch07_sealed_classes;

import java.util.List;

/**
 * Java 17 — Sealed Classes (JEP 409)
 * Matches blog article: 07-sealed-classes.md
 */
public class SealedClassExamples {

    // ── 1. Basic sealed interface + permitted implementations ─────────────────
    sealed interface Shape permits Circle, Rectangle, Triangle {}

    record Circle(double radius) implements Shape {}
    record Rectangle(double width, double height) implements Shape {}
    // Triangle is non-sealed — anyone can extend it
    non-sealed class Triangle implements Shape {
        final double base, height;
        Triangle(double base, double height) {
            this.base = base;
            this.height = height;
        }
        @Override public String toString() {
            return "Triangle[base=" + base + ", height=" + height + "]";
        }
    }

    static double area(Shape s) {
        // switch over sealed type — compiler knows all permitted subtypes (Java 17 preview JEP 406)
        return switch (s) {
            case Circle c    -> Math.PI * c.radius() * c.radius();
            case Rectangle r -> r.width() * r.height();
            case Triangle t  -> 0.5 * t.base * t.height;
        };
    }

    // ── 2. Sealed class hierarchy (abstract class variant) ────────────────────
    sealed abstract static class Result<T> permits Result.Success, Result.Failure {

        public static final class Success<T> extends Result<T> {
            private final T value;
            public Success(T value) { this.value = value; }
            public T value() { return value; }
            @Override public String toString() { return "Success(" + value + ")"; }
        }

        public static final class Failure<T> extends Result<T> {
            private final String message;
            public Failure(String message) { this.message = message; }
            public String message() { return message; }
            @Override public String toString() { return "Failure(" + message + ")"; }
        }
    }

    static Result<Integer> divide(int a, int b) {
        if (b == 0) return new Result.Failure<>("Division by zero");
        return new Result.Success<>(a / b);
    }

    static void handleResult(Result<Integer> result) {
        if (result instanceof Result.Success<Integer> s)
            System.out.println("Result: " + s.value());
        else if (result instanceof Result.Failure<Integer> f)
            System.out.println("Error: " + f.message());
    }

    // ── 3. Sealed interface for a payment domain model ─────────────────────────
    sealed interface PaymentMethod permits CreditCard, BankTransfer, Crypto {}

    record CreditCard(String last4, String network) implements PaymentMethod {}
    record BankTransfer(String sortCode, String accountNumber) implements PaymentMethod {}
    record Crypto(String walletAddress, String currency) implements PaymentMethod {}

    static double processingFee(PaymentMethod method, double amount) {
        return switch (method) {
            case CreditCard cc && cc.network().equals("AMEX") -> amount * 0.035;
            case CreditCard cc                                   -> amount * 0.015;
            case BankTransfer bt                                 -> 0.25;
            case Crypto c                                        -> amount * 0.005;
        };
    }

    static String formatMethod(PaymentMethod method) {
        return switch (method) {
            case CreditCard cc   -> "Card ending " + cc.last4() + " (" + cc.network() + ")";
            case BankTransfer bt -> "Bank transfer " + bt.sortCode() + "/" + bt.accountNumber();
            case Crypto c        -> c.currency() + " wallet " + c.walletAddress().substring(0, 8) + "...";
        };
    }

    // ── 4. Sealed interface for an event bus ──────────────────────────────────
    sealed interface DomainEvent permits UserRegistered, OrderPlaced, PaymentProcessed {}
    record UserRegistered(String userId, String email) implements DomainEvent {}
    record OrderPlaced(String orderId, String userId, double total) implements DomainEvent {}
    record PaymentProcessed(String orderId, boolean success) implements DomainEvent {}

    static void handleEvent(DomainEvent event) {
        String log = switch (event) {
            case UserRegistered e ->
                String.format("User %s registered with %s", e.userId(), e.email());
            case OrderPlaced e ->
                String.format("Order %s placed by %s for £%.2f", e.orderId(), e.userId(), e.total());
            case PaymentProcessed e && e.success() ->
                String.format("Payment for order %s SUCCEEDED", e.orderId());
            case PaymentProcessed e ->
                String.format("Payment for order %s FAILED", e.orderId());
        };
        System.out.println("[EVENT] " + log);
    }

    public static void main(String[] args) {
        System.out.println("=== Shape Areas ===");
        List<Shape> shapes = List.of(
            new Circle(5),
            new Rectangle(4, 6),
            new Triangle(3, 8)
        );
        shapes.forEach(s -> System.out.printf("  %s → %.2f%n", s, area(s)));

        System.out.println("\n=== Result Type ===");
        handleResult(divide(10, 2));
        handleResult(divide(10, 0));

        System.out.println("\n=== Payment Methods ===");
        double orderAmount = 100.0;
        List<PaymentMethod> methods = List.of(
            new CreditCard("4242", "VISA"),
            new CreditCard("0005", "AMEX"),
            new BankTransfer("20-00-00", "12345678"),
            new Crypto("0xabc123def456", "ETH")
        );
        methods.forEach(m -> System.out.printf("  %s → fee: £%.2f%n",
            formatMethod(m), processingFee(m, orderAmount)));

        System.out.println("\n=== Domain Events ===");
        List<DomainEvent> events = List.of(
            new UserRegistered("u001", "alice@example.com"),
            new OrderPlaced("o001", "u001", 89.99),
            new PaymentProcessed("o001", true),
            new OrderPlaced("o002", "u001", 199.00),
            new PaymentProcessed("o002", false)
        );
        events.forEach(SealedClassExamples::handleEvent);
    }
}

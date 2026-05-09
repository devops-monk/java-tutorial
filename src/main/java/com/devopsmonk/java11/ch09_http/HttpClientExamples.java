package com.devopsmonk.java11.ch09_http;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Chapter 09 — HTTP Client API (JEP 321): HTTP/2, Async, Authentication
 * Tutorial: https://devops-monk.com/tutorials/java11/http-client-api/
 *
 * Java 11 ships a full-featured HTTP client in java.net.http.
 * Before Java 11: HttpURLConnection (verbose, HTTP/1.1 only) or third-party
 *   libraries like Apache HttpClient, OkHttp, Unirest.
 *
 * Key classes:
 *   HttpClient       — the connection pool and configuration (reusable, thread-safe)
 *   HttpRequest      — immutable request (URL, method, headers, body)
 *   HttpResponse<T>  — response with typed body (String, byte[], InputStream, …)
 *   BodyPublishers   — factory for request bodies
 *   BodyHandlers     — factory for response body types
 *
 * NOTE: Examples that call real URLs (https://httpbin.org) will need network.
 *       All examples include a try/catch so they run fine offline too.
 */
public class HttpClientExamples {

    // Create ONE client — it manages connection pools, thread pools, and HTTP/2
    static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)          // prefer HTTP/2, fall back to 1.1
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL) // follow 3xx but not HTTP→HTTPS
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 09: HTTP Client API ===\n");

        buildingRequests();
        synchronousGet();
        asyncGet();
        postWithBody();
        parallelRequests();
        headersAndStatus();
        timeoutHandling();
        httpClientComparison();
    }

    // -------------------------------------------------------------------------
    // 1. Building Requests — HttpRequest is immutable and reusable
    // -------------------------------------------------------------------------
    static void buildingRequests() {
        System.out.println("--- 1. Building HttpRequests ---");

        // GET request
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/get"))
                .GET()
                .header("Accept", "application/json")
                .header("User-Agent", "java11-tutorial/1.0")
                .timeout(Duration.ofSeconds(10))
                .build();

        System.out.println("  GET  " + getRequest.uri());
        System.out.println("  Method: " + getRequest.method());
        System.out.println("  Headers: " + getRequest.headers().map());

        // POST with JSON body
        String json = "{\"name\":\"Alice\",\"department\":\"Engineering\"}";
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/post"))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();

        System.out.println("  POST " + postRequest.uri());
        System.out.println("  Body publisher: " + postRequest.bodyPublisher().map(p -> p.contentLength() + " bytes").orElse("none"));

        // PUT, DELETE, PATCH
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/put"))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"id\":1}"))
                .build();

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/delete"))
                .DELETE()
                .build();

        System.out.println("  PUT  " + putRequest.uri());
        System.out.println("  DEL  " + deleteRequest.uri());
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. Synchronous GET — simplest usage
    // -------------------------------------------------------------------------
    static void synchronousGet() {
        System.out.println("--- 2. Synchronous GET ---");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://httpbin.org/get?name=Alice"))
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();

            // send() blocks until the response is complete
            HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());

            System.out.println("  Status:  " + response.statusCode());
            System.out.println("  Version: " + response.version());

            // Print first 200 chars of the body
            String body = response.body();
            System.out.println("  Body (first 200 chars):");
            System.out.println("    " + body.substring(0, Math.min(body.length(), 200)));

        } catch (java.net.ConnectException | java.io.IOException e) {
            System.out.println("  [offline demo] GET request — would return HTTP 200 with JSON body");
            System.out.println("  Status: 200, Body: {\"url\": \"https://httpbin.org/get?name=Alice\", ...}");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 3. Asynchronous GET — non-blocking with CompletableFuture
    // -------------------------------------------------------------------------
    static void asyncGet() throws Exception {
        System.out.println("--- 3. Asynchronous GET (sendAsync) ---");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/delay/1"))
                .timeout(Duration.ofSeconds(10))
                .build();

        // sendAsync returns immediately — no thread blocked
        CompletableFuture<String> future = CLIENT
                .sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    System.out.println("  [async] Status: " + response.statusCode());
                    return response.body();
                })
                .exceptionally(ex -> "[offline] async response: " + ex.getCause().getMessage());

        System.out.println("  Request sent — doing other work while waiting...");
        System.out.println("  Other work done.");

        // Wait for the result
        String result = future.get(15, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("  Async result length: " + result.length() + " chars");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. POST with JSON body
    // -------------------------------------------------------------------------
    static void postWithBody() {
        System.out.println("--- 4. POST with JSON body ---");

        String jsonBody = """
                {
                    "name": "Alice",
                    "department": "Engineering",
                    "salary": 95000
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/post"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer my-token-123")
                .timeout(Duration.ofSeconds(8))
                .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());
            System.out.println("  POST status: " + response.statusCode());
            // httpbin echoes back your request — look for "json" key
            int jsonStart = response.body().indexOf("\"json\"");
            if (jsonStart >= 0) {
                System.out.println("  Echoed JSON section found in response ✓");
            }
        } catch (Exception e) {
            System.out.println("  [offline demo] POST with JSON body");
            System.out.println("  Would send: " + jsonBody.strip());
        }

        // Other BodyPublishers:
        System.out.println("  BodyPublishers available:");
        System.out.println("    ofString(s)        — UTF-8 String");
        System.out.println("    ofByteArray(b)     — raw bytes");
        System.out.println("    ofFile(path)       — stream a file");
        System.out.println("    ofInputStream(sup) — from an InputStream");
        System.out.println("    noBody()           — empty body (for GET/DELETE)");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Parallel async requests — fetch multiple URLs concurrently
    // -------------------------------------------------------------------------
    static void parallelRequests() throws Exception {
        System.out.println("--- 5. Parallel Async Requests ---");

        List<String> urls = List.of(
                "https://httpbin.org/get?id=1",
                "https://httpbin.org/get?id=2",
                "https://httpbin.org/get?id=3"
        );

        long start = System.currentTimeMillis();

        // Fire all requests at once — they run concurrently
        List<CompletableFuture<String>> futures = urls.stream()
                .map(url -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .build())
                .map(req -> CLIENT.sendAsync(req, BodyHandlers.ofString())
                        .thenApply(r -> r.statusCode() + " " + url)
                        .exceptionally(ex -> "OFFLINE " + url))
                .collect(Collectors.toList());

        // Wait for all
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(20, java.util.concurrent.TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  " + urls.size() + " parallel requests in " + elapsed + "ms (sequential would be 3x slower):");
        futures.forEach(f -> {
            try { System.out.println("    " + f.get()); }
            catch (Exception ignored) {}
        });

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Reading Headers and Status
    // -------------------------------------------------------------------------
    static void headersAndStatus() {
        System.out.println("--- 6. Response Headers and Status ---");

        try {
            HttpResponse<Void> response = CLIENT.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("https://httpbin.org/response-headers?X-Custom-Header=java11"))
                            .build(),
                    BodyHandlers.discarding()  // we only care about headers
            );

            System.out.println("  Status: " + response.statusCode());
            System.out.println("  Content-Type: " + response.headers().firstValue("content-type").orElse("none"));
            System.out.println("  All headers:");
            response.headers().map().forEach((k, v) ->
                    System.out.println("    " + k + ": " + v));

        } catch (Exception e) {
            System.out.println("  [offline demo] Response headers:");
            System.out.println("    content-type: application/json");
            System.out.println("    x-custom-header: java11");
            System.out.println("    server: gunicorn/19.9.0");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Timeout Handling
    // -------------------------------------------------------------------------
    static void timeoutHandling() {
        System.out.println("--- 7. Timeout Handling ---");

        // Request-level timeout
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/delay/10"))   // 10s delay
                .timeout(Duration.ofSeconds(2))                     // 2s timeout
                .build();

        try {
            CLIENT.send(request, BodyHandlers.ofString());
            System.out.println("  Request completed (unexpected)");
        } catch (HttpTimeoutException e) {
            System.out.println("  HttpTimeoutException caught: " + e.getMessage());
            System.out.println("  → Use circuit breaker or retry logic here");
        } catch (Exception e) {
            System.out.println("  Network error (offline): " + e.getClass().getSimpleName());
            System.out.println("  → In real use: HttpTimeoutException would be thrown after 2s");
        }

        // Best practices
        System.out.println("  Timeout best practices:");
        System.out.println("    ✓ Always set request.timeout() — never block forever");
        System.out.println("    ✓ Set client connectTimeout in the builder too");
        System.out.println("    ✓ Wrap in retry/circuit-breaker (Resilience4j, etc.)");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 8. Migration: HttpURLConnection → HttpClient
    // -------------------------------------------------------------------------
    static void httpClientComparison() {
        System.out.println("--- 8. Migration: HttpURLConnection → HttpClient ---");

        System.out.println(
            "  BEFORE (Java 8 — HttpURLConnection):\n" +
            "    URL url = new URL(\"https://api.example.com/data\");\n" +
            "    HttpURLConnection conn = (HttpURLConnection) url.openConnection();\n" +
            "    conn.setRequestMethod(\"GET\");\n" +
            "    conn.setRequestProperty(\"Accept\", \"application/json\");\n" +
            "    conn.connect();\n" +
            "    int status = conn.getResponseCode();\n" +
            "    try (BufferedReader br = new BufferedReader(\n" +
            "             new InputStreamReader(conn.getInputStream()))) {\n" +
            "        StringBuilder sb = new StringBuilder();\n" +
            "        String line; while ((line = br.readLine()) != null) sb.append(line);\n" +
            "        String body = sb.toString();\n" +
            "    }\n" +
            "\n" +
            "  AFTER (Java 11 — HttpClient):\n" +
            "    HttpClient client = HttpClient.newHttpClient();\n" +
            "    HttpRequest req = HttpRequest.newBuilder()\n" +
            "        .uri(URI.create(\"https://api.example.com/data\"))\n" +
            "        .header(\"Accept\", \"application/json\")\n" +
            "        .build();\n" +
            "    HttpResponse<String> res = client.send(req, BodyHandlers.ofString());\n" +
            "    int status = res.statusCode();\n" +
            "    String body = res.body();\n\n" +
            "  Key advantages:\n" +
            "    ✓ HTTP/2 support (multiplexing, server push)\n" +
            "    ✓ Built-in async via sendAsync() + CompletableFuture\n" +
            "    ✓ Immutable, thread-safe request/response objects\n" +
            "    ✓ Clean timeout configuration\n" +
            "    ✓ Typed response body handlers\n"
        );
    }
}

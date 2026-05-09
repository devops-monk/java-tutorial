package com.devopsmonk.java11.ch13_security;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.net.ssl.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Chapter 13 — Security: TLS 1.3, ChaCha20, Curve25519 (JEP 329, 332, 324)
 * Tutorial: https://devops-monk.com/tutorials/java11/security-tls13/
 *
 * Java 11 security improvements:
 *   TLS 1.3  (JEP 332) — faster handshake (1-RTT vs 2-RTT), forward secrecy by default
 *   ChaCha20-Poly1305  — AEAD cipher, faster than AES on mobile/no-AES-NI hardware
 *   Curve25519/448     — modern elliptic curves for key agreement (JEP 324)
 *   SHA-3              — Keccak-based hash family (Java 9, JEP 287)
 *   Nest-Based Access  — JVM-level fix for private access in nested classes (JEP 181)
 */
public class SecurityExamples {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ch 13: Security Enhancements ===\n");

        tls13Overview();
        sha3Hashing();
        aesGcmEncryption();
        chacha20Encryption();
        curve25519KeyExchange();
        passwordHashingBestPractice();
        tlsClientConfiguration();
    }

    // -------------------------------------------------------------------------
    // 1. TLS 1.3 Overview
    // -------------------------------------------------------------------------
    static void tls13Overview() {
        System.out.println("--- 1. TLS 1.3 (JEP 332) ---");

        System.out.println(
            "  TLS 1.3 improvements over TLS 1.2:\n" +
            "    ✓ Faster handshake: 1-RTT (vs 2-RTT in TLS 1.2)\n" +
            "    ✓ 0-RTT session resumption (experimental, disabled by default)\n" +
            "    ✓ Removed weak cipher suites (RC4, 3DES, MD5, SHA-1 in handshake)\n" +
            "    ✓ Forward secrecy mandated — all cipher suites use ECDHE or DHE\n" +
            "    ✓ Encrypted handshake — certificate exchange is now encrypted\n\n" +
            "  Java 11 TLS 1.3 cipher suites (the only ones supported):\n" +
            "    TLS_AES_128_GCM_SHA256\n" +
            "    TLS_AES_256_GCM_SHA384\n" +
            "    TLS_CHACHA20_POLY1305_SHA256\n\n" +
            "  System properties:\n" +
            "    -Djdk.tls.client.protocols=TLSv1.3       — force TLS 1.3 only\n" +
            "    -Dhttps.protocols=TLSv1.2,TLSv1.3        — allow both\n" +
            "    jdk.tls.disabledAlgorithms in java.security  — disable weak algorithms\n"
        );

        // Show what TLS protocols and cipher suites are available
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            SSLParameters params = ctx.getDefaultSSLParameters();
            System.out.println("  Default TLS protocols:");
            for (String p : params.getProtocols())
                System.out.println("    " + p);
            System.out.println("  Supported TLS 1.3 cipher suites:");
            for (String cs : params.getCipherSuites())
                if (cs.startsWith("TLS_AES") || cs.startsWith("TLS_CHACHA"))
                    System.out.println("    " + cs);
        } catch (Exception e) {
            System.out.println("  Could not inspect TLS context: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 2. SHA-3 Hashing (Java 9, JEP 287)
    // -------------------------------------------------------------------------
    static void sha3Hashing() throws Exception {
        System.out.println("--- 2. SHA-3 Hashing ---");

        String input = "Hello, Java 11 Security!";
        byte[] data  = input.getBytes(StandardCharsets.UTF_8);

        // SHA-3 variants: SHA3-224, SHA3-256, SHA3-384, SHA3-512
        String[] algorithms = {"SHA-256", "SHA3-256", "SHA-512", "SHA3-512"};

        System.out.println("  Input: \"" + input + "\"");
        System.out.println();
        System.out.printf("  %-12s  %s%n", "Algorithm", "Hash (hex)");
        System.out.println("  " + "-".repeat(80));

        for (String alg : algorithms) {
            try {
                MessageDigest md = MessageDigest.getInstance(alg);
                byte[] hash = md.digest(data);
                String hex = HexFormat.of().formatHex(hash);
                System.out.printf("  %-12s  %s%n", alg, hex.substring(0, Math.min(hex.length(), 64)) + "...");
            } catch (NoSuchAlgorithmException e) {
                System.out.printf("  %-12s  NOT AVAILABLE%n", alg);
            }
        }

        // SHA-3 is Keccak-based — fundamentally different construction from SHA-2
        // Use SHA-3 when you need defense-in-depth (different algorithm family)
        System.out.println("\n  SHA-3 uses a Keccak sponge construction — independent of SHA-2.");
        System.out.println("  Use case: when protocol requires SHA-3 (NIST standards, blockchain).");
        System.out.println("  For most apps: SHA-256 or SHA3-256 are both fine.\n");
    }

    // -------------------------------------------------------------------------
    // 3. AES-GCM Encryption — the standard choice for symmetric encryption
    // -------------------------------------------------------------------------
    static void aesGcmEncryption() throws Exception {
        System.out.println("--- 3. AES-256-GCM Encryption ---");

        String plaintext = "Sensitive employee data: Alice, £95,000, SSN: 123-45-6789";

        // 1. Generate a random 256-bit AES key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        SecretKey key = keyGen.generateKey();

        // 2. Generate a random 12-byte IV (GCM standard)
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);  // 128-bit auth tag

        // 3. Encrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        System.out.println("  Plaintext:  " + plaintext);
        System.out.println("  Ciphertext: " + Base64.getEncoder().encodeToString(ciphertext));
        System.out.println("  IV:         " + Base64.getEncoder().encodeToString(iv));

        // 4. Decrypt — must use same key and IV
        Cipher decipher = Cipher.getInstance("AES/GCM/NoPadding");
        decipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] decrypted = decipher.doFinal(ciphertext);

        System.out.println("  Decrypted:  " + new String(decrypted, StandardCharsets.UTF_8));
        System.out.println("  GCM tag verifies authenticity — tampering causes AEADBadTagException");

        // Key rules for AES-GCM:
        System.out.println("\n  AES-GCM best practices:");
        System.out.println("    ✓ Use a fresh random IV for every encryption operation");
        System.out.println("    ✓ Store IV alongside ciphertext (IV is not secret)");
        System.out.println("    ✓ Never reuse (key, IV) pair — catastrophic for GCM security");
        System.out.println("    ✓ Use 256-bit keys for new systems");
        System.out.println("    ✗ Never use AES/ECB — identical plaintext blocks give identical ciphertext");

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 4. ChaCha20-Poly1305 (JEP 329)
    // -------------------------------------------------------------------------
    static void chacha20Encryption() throws Exception {
        System.out.println("--- 4. ChaCha20-Poly1305 ---");

        System.out.println(
            "  ChaCha20-Poly1305 is an AEAD cipher (like AES-GCM) but:\n" +
            "    ✓ Faster on CPUs without AES hardware acceleration (mobile, IoT, older servers)\n" +
            "    ✓ Simpler to implement securely (no padding, counter-based nonce)\n" +
            "    ✓ Used by TLS 1.3 as TLS_CHACHA20_POLY1305_SHA256\n\n" +
            "  Use AES-GCM when:  hardware AES-NI is available (most modern x86/ARM)\n" +
            "  Use ChaCha20 when: software-only crypto, embedded, or cross-platform compatibility\n"
        );

        try {
            String plaintext = "ChaCha20 is fast and secure!";

            KeyGenerator keyGen = KeyGenerator.getInstance("ChaCha20");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();

            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new IvParameterSpec(nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            System.out.println("  Plaintext:  " + plaintext);
            System.out.println("  Ciphertext: " + Base64.getEncoder().encodeToString(ciphertext));

            // Decrypt
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(nonce));
            String decrypted = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            System.out.println("  Decrypted:  " + decrypted);

        } catch (NoSuchAlgorithmException e) {
            System.out.println("  ChaCha20 not available on this JDK build: " + e.getMessage());
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 5. Curve25519 Key Exchange (JEP 324)
    // -------------------------------------------------------------------------
    static void curve25519KeyExchange() throws Exception {
        System.out.println("--- 5. Curve25519 Key Agreement ---");

        System.out.println(
            "  Curve25519 (X25519) is a modern elliptic curve for Diffie-Hellman key exchange.\n" +
            "  Advantages over traditional ECDH with P-256:\n" +
            "    ✓ Faster — constant-time operations, no branch-timing attacks\n" +
            "    ✓ Simpler — no curve parameters to choose wrong\n" +
            "    ✓ Used in TLS 1.3, Signal protocol, WireGuard\n"
        );

        try {
            // Generate key pairs for Alice and Bob
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            KeyPair aliceKP = kpg.generateKeyPair();
            KeyPair bobKP   = kpg.generateKeyPair();

            // Alice computes shared secret using her private key + Bob's public key
            KeyAgreement aliceKA = KeyAgreement.getInstance("XDH");
            aliceKA.init(aliceKP.getPrivate());
            aliceKA.doPhase(bobKP.getPublic(), true);
            byte[] aliceSecret = aliceKA.generateSecret();

            // Bob computes shared secret using his private key + Alice's public key
            KeyAgreement bobKA = KeyAgreement.getInstance("XDH");
            bobKA.init(bobKP.getPrivate());
            bobKA.doPhase(aliceKP.getPublic(), true);
            byte[] bobSecret = bobKA.generateSecret();

            System.out.println("  Alice secret: " + HexFormat.of().formatHex(aliceSecret).substring(0, 32) + "...");
            System.out.println("  Bob secret:   " + HexFormat.of().formatHex(bobSecret).substring(0, 32) + "...");
            System.out.println("  Secrets match: " + MessageDigest.isEqual(aliceSecret, bobSecret));
            System.out.println("  → Both can now derive the same AES key without ever sharing it");

        } catch (NoSuchAlgorithmException e) {
            System.out.println("  X25519 not available: " + e.getMessage());
            System.out.println("  Requires Java 11+ and the SunEC provider.");
        }

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 6. Password Hashing Best Practice
    // -------------------------------------------------------------------------
    static void passwordHashingBestPractice() throws Exception {
        System.out.println("--- 6. Password Hashing Best Practices ---");

        System.out.println(
            "  NEVER store passwords as SHA-256/SHA-512 hashes — too fast for brute force.\n\n" +
            "  Use a dedicated password hashing function:\n" +
            "    BCrypt    — Java: spring-security-crypto or jBCrypt library\n" +
            "    PBKDF2    — built into Java (SecretKeyFactory)\n" +
            "    Argon2    — winner of Password Hashing Competition (use argon2-jvm library)\n\n" +
            "  PBKDF2 with Java's built-in crypto:\n"
        );

        // PBKDF2 with HMAC-SHA256 — built into Java, no extra library needed
        String password = "mysecretpassword";
        byte[] salt     = new byte[16];
        new SecureRandom().nextBytes(salt);

        int iterations = 310_000;  // OWASP 2023 recommendation for PBKDF2-HMAC-SHA256
        int keyLength  = 256;      // bits

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = skf.generateSecret(spec).getEncoded();

        System.out.println("  PBKDF2WithHmacSHA256:");
        System.out.println("  Salt (store with hash): " + Base64.getEncoder().encodeToString(salt));
        System.out.println("  Hash:                   " + Base64.getEncoder().encodeToString(hash));
        System.out.println("  Iterations:             " + iterations + " (OWASP 2023 recommendation)");

        // Verify: re-hash with same salt and compare
        PBEKeySpec verifySpec = new PBEKeySpec(
                password.toCharArray(), salt, iterations, keyLength);
        byte[] verifyHash = skf.generateSecret(verifySpec).getEncoded();
        System.out.println("  Password verified: " + MessageDigest.isEqual(hash, verifyHash));

        System.out.println();
    }

    // -------------------------------------------------------------------------
    // 7. Configuring TLS for the HTTP Client
    // -------------------------------------------------------------------------
    static void tlsClientConfiguration() throws Exception {
        System.out.println("--- 7. TLS Client Configuration ---");

        System.out.println(
            "  // Force TLS 1.3 in the HTTP Client:\n" +
            "  SSLContext sslContext = SSLContext.getInstance(\"TLSv1.3\");\n" +
            "  sslContext.init(null, null, null);\n\n" +
            "  HttpClient client = HttpClient.newBuilder()\n" +
            "      .sslContext(sslContext)\n" +
            "      .build();\n\n" +
            "  // Custom trust manager (accept a self-signed cert for testing):\n" +
            "  TrustManager[] tm = new TrustManager[] {\n" +
            "      new X509TrustManager() {\n" +
            "          public void checkClientTrusted(X509Certificate[] c, String t) {}\n" +
            "          public void checkServerTrusted(X509Certificate[] c, String t) {}\n" +
            "          public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }\n" +
            "      }\n" +
            "  };\n" +
            "  // ↑ Only for testing/local dev — NEVER in production\n\n" +
            "  // Production checklist:\n" +
            "    ✓ Use TLS 1.2 minimum, TLS 1.3 preferred\n" +
            "    ✓ Disable SSLv3, TLSv1.0, TLSv1.1 via jdk.tls.disabledAlgorithms\n" +
            "    ✓ Pin certificates for high-security APIs (certificate pinning)\n" +
            "    ✓ Rotate certificates before expiry (monitor via CertificateExpiry JFR event)\n" +
            "    ✓ Always verify server certificates in production HttpClient\n"
        );

        // Show current disabled TLS algorithms from security configuration
        String disabledAlgs = Security.getProperty("jdk.tls.disabledAlgorithms");
        if (disabledAlgs != null) {
            System.out.println("  Currently disabled TLS algorithms (java.security):");
            for (String alg : disabledAlgs.split(",")) {
                System.out.println("    " + alg.strip());
            }
        }
    }
}

package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Force Netty to use JDK SSL instead of OpenSSL/BoringSSL.
        // The Cosmos DB Java SDK uses Netty, and OpenSSL bypasses the JDK
        // truststore where the emulator's self-signed cert is imported.
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Only apply trust-all SSL for the local Cosmos DB emulator (self-signed cert).
        // In CI, the emulator cert is imported into the JDK truststore, but this
        // serves as a defense-in-depth fallback for local development.
        String cosmosEndpoint = System.getenv().getOrDefault("COSMOS_ENDPOINT", "https://localhost:8081");
        if (cosmosEndpoint.contains("localhost") || cosmosEndpoint.contains("127.0.0.1")) {
            try {
                TrustManager[] emulatorTrustManager = new TrustManager[]{
                    new EmulatorTrustManager()
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, emulatorTrustManager, new java.security.SecureRandom());
                SSLContext.setDefault(sc);
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) ->
                    "localhost".equals(hostname) || "127.0.0.1".equals(hostname));
            } catch (Exception e) {
                System.err.println("Warning: Could not set up emulator SSL context: " + e.getMessage());
            }
        }

        SpringApplication.run(Application.class, args);
    }

    /**
     * TrustManager that accepts the Cosmos DB emulator's self-signed certificate.
     * Only activated when COSMOS_ENDPOINT points to localhost/127.0.0.1.
     */
    static class EmulatorTrustManager implements X509TrustManager {
        @Override
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override
        public void checkClientTrusted(X509Certificate[] certs, String type) { }
        @Override
        public void checkServerTrusted(X509Certificate[] certs, String type) { }
    }
}

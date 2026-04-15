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

        // Set up a trust-all SSLContext as defense-in-depth for the emulator
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String type) {}
                    public void checkServerTrusted(X509Certificate[] certs, String type) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) ->
                hostname.equals("localhost") || hostname.equals("127.0.0.1"));
        } catch (Exception e) {
            System.err.println("Warning: Could not set up trust-all SSL context: " + e.getMessage());
        }

        SpringApplication.run(Application.class, args);
    }
}

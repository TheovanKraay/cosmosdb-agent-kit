package com.ecommerce.orderapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Disable OpenSSL so Netty uses JDK SSL (required for emulator trust-all)
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Register a custom security Provider that replaces the default PKIX
        // TrustManagerFactory with a trust-all implementation. This is required
        // because Netty creates its own SSL context using
        // TrustManagerFactory.getInstance("PKIX") — it does NOT use
        // SSLContext.getDefault(). Without this, the Cosmos DB Emulator's
        // self-signed certificate fails PKIX signature validation.
        Provider trustAllProvider = new Provider("TrustAll", "1.0",
                "Trust-all TrustManagerFactory for Cosmos DB Emulator") {};
        trustAllProvider.put("TrustManagerFactory.PKIX",
                "com.ecommerce.orderapi.TrustAllTrustManagerFactorySpi");
        trustAllProvider.put("TrustManagerFactory.SunX509",
                "com.ecommerce.orderapi.TrustAllTrustManagerFactorySpi");
        Security.insertProviderAt(trustAllProvider, 1);

        // Also install trust-all SSLContext for HttpsURLConnection (Phase 1 polling)
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("Failed to configure trust-all SSL: " + e.getMessage());
        }

        SpringApplication.run(Application.class, args);
    }
}

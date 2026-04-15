package com.ecommerce.orders;

import com.ecommerce.orders.ssl.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;

@SpringBootApplication
public class EcommerceOrderApiApplication {

    public static void main(String[] args) {
        // Disable OpenSSL to force JDK SSL provider (required for emulator)
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Register trust-all provider at position 1 so Reactor Netty picks it up
        Security.insertProviderAt(new TrustAllProvider(), 1);

        // Also set default SSLContext for HttpsURLConnection (used by health polling)
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            SSLContext.setDefault(sslContext);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
                return "localhost".equals(hostname) || "127.0.0.1".equals(hostname);
            });
        } catch (Exception e) {
            System.err.println("WARNING: Failed to configure trust-all SSL: " + e.getMessage());
        }

        SpringApplication.run(EcommerceOrderApiApplication.class, args);
    }
}

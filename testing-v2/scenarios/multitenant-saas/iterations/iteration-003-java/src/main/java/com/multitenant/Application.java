package com.multitenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        // Force JDK SSL instead of Netty/OpenSSL (needed for emulator self-signed cert)
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Trust all certificates (for Cosmos DB emulator's self-signed cert)
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
        } catch (Exception e) {
            System.err.println("Warning: Could not set trust-all SSL context: " + e.getMessage());
        }

        SpringApplication.run(Application.class, args);
    }
}

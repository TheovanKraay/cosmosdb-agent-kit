package com.multitenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        // Disable Netty native OpenSSL so JDK SSL engine is used.
        // This ensures the trust-all SSLContext below takes effect.
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Trust all certificates for Cosmos DB Emulator connectivity.
        // The emulator uses a self-signed cert that may fail signature
        // verification through Netty's LazyX509Certificate wrapper.
        disableSslVerification();

        SpringApplication.run(Application.class, args);
    }

    private static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            SSLContext.setDefault(sc);
        } catch (Exception e) {
            System.err.println("Warning: Could not set trust-all SSL context: " + e.getMessage());
        }
    }
}

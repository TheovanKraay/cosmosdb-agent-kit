package com.multitenant;

import com.multitenant.ssl.TrustAllProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        // Disable Netty native OpenSSL so JDK SSL engine is used.
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "true");

        // Trust all certificates for Cosmos DB Emulator connectivity.
        disableSslVerification();

        SpringApplication.run(Application.class, args);
    }

    static void disableSslVerification() {
        try {
            // Register custom Security Provider that overrides PKIX TrustManagerFactory
            // to trust all certificates. This is required because Reactor Netty (used
            // by the Cosmos SDK) creates its own SSL context using
            // TrustManagerFactory.getInstance("PKIX") — it does NOT use
            // SSLContext.getDefault(). This provider intercepts that call.
            Security.insertProviderAt(new TrustAllProvider(), 1);

            // Also set the default SSLContext for any non-Netty HTTP clients
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
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("Warning: Could not set trust-all SSL context: " + e.getMessage());
        }
    }
}

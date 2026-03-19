package com.ecommerce.config;

import javax.net.ssl.*;
import java.security.*;
import java.security.cert.X509Certificate;

/**
 * Disables SSL certificate validation globally for connecting to Cosmos DB Emulator.
 * Must be called before any SSL connections are made.
 */
public class TrustAllSslProvider {

    public static void install() {
        try {
            // 1. Set SSLContext.setDefault() with trust-all TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            // 2. Register a custom Security provider that overrides PKIX TrustManagerFactory
            //    This ensures that any code (including Reactor Netty / Azure SDK) that creates
            //    a new TrustManagerFactory using the default PKIX algorithm will get our
            //    trust-all implementation instead of the default JDK one.
            Security.insertProviderAt(new TrustAllSecurityProvider(), 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install trust-all SSL provider", e);
        }
    }

    /**
     * Custom security provider that overrides the PKIX and SunX509 TrustManagerFactory
     * algorithms to use trust-all implementations.
     */
    private static class TrustAllSecurityProvider extends Provider {
        TrustAllSecurityProvider() {
            super("TrustAllProvider", "1.0", "Trust-all TrustManagerFactory provider");
            put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactorySpi.class.getName());
            put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactorySpi.class.getName());
        }
    }

    /**
     * TrustManagerFactory implementation that always returns trust-all TrustManagers.
     */
    public static class TrustAllTrustManagerFactorySpi extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) {}

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {}

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                }
            };
        }
    }
}

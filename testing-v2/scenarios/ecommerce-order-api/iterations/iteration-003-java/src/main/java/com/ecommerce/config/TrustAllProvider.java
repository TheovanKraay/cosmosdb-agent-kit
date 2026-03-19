package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

/**
 * Custom Security Provider that overrides PKIX and SunX509 TrustManagerFactory
 * algorithms to trust all certificates. This is necessary because Reactor Netty
 * creates its own TrustManagerFactory via TrustManagerFactory.getInstance("PKIX"),
 * which bypasses the JVM default SSLContext.
 *
 * Must be installed via Security.insertProviderAt() BEFORE Spring starts.
 */
public class TrustAllProvider extends Provider {

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust-all TrustManagerFactory provider");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactorySpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactorySpi.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static class TrustAllTrustManagerFactorySpi extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) {
            // No-op
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
            // No-op
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all
                    }
                }
            };
        }
    }
}

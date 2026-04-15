package com.ecommerce.order.ssl;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;

/**
 * Custom Security Provider that overrides the PKIX TrustManagerFactory
 * to return trust-all TrustManagers. This allows the Java SDK (via Netty)
 * to accept the Cosmos DB emulator's self-signed certificate.
 *
 * Reactor Netty creates its own SSL context via TrustManagerFactory.getInstance("PKIX"),
 * ignoring SSLContext.getDefault(). This provider intercepts that call.
 */
public class TrustAllProvider extends Provider {

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust-all TrustManagerFactory for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactorySpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactorySpi.class.getName());
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
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
        }
    }
}

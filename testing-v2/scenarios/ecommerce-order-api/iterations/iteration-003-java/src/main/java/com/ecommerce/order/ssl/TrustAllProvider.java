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
 * Custom security provider that trusts all certificates.
 * Used for connecting to the Cosmos DB Emulator's self-signed certificate.
 * This overrides the PKIX TrustManagerFactory so that Reactor Netty
 * (which creates its own SSL context) also uses trust-all.
 */
public class TrustAllProvider extends Provider {

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust-all provider for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactory.class.getName());
    }

    public static class TrustAllTrustManagerFactory extends TrustManagerFactorySpi {

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
            return new TrustManager[]{new X509TrustManager() {
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
            }};
        }
    }
}

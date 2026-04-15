package com.ecommerce.orders.ssl;

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
 * to trust all certificates. Required for Cosmos DB Emulator because
 * Reactor Netty creates its own SSL context via TrustManagerFactory.getInstance("PKIX"),
 * ignoring SSLContext.getDefault().
 */
public class TrustAllProvider extends Provider {

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust-all TrustManagerFactory provider");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactory.class.getName());
    }

    public static class TrustAllTrustManagerFactory extends TrustManagerFactorySpi {

        private static final TrustManager[] TRUST_ALL = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        @Override
        protected void engineInit(KeyStore ks) {
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return TRUST_ALL;
        }
    }
}

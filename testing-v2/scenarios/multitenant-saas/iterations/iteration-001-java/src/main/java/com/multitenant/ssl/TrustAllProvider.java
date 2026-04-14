package com.multitenant.ssl;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;

/**
 * Custom Java Security Provider that overrides the PKIX TrustManagerFactory
 * to trust all certificates. This is required for Cosmos DB Emulator
 * connectivity because Reactor Netty (used by the Cosmos SDK) creates its
 * own SSL context using TrustManagerFactory.getInstance("PKIX") — it does
 * NOT use SSLContext.getDefault(). Setting SSLContext.setDefault() with a
 * trust-all TrustManager has no effect on Reactor Netty.
 *
 * By registering this provider at position 1, all TrustManagerFactory.getInstance()
 * calls return trust-all managers, including those from Reactor Netty.
 *
 * WARNING: This is for local development / emulator only. Never use in production.
 */
public class TrustAllProvider extends Provider {

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust all certificates for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllManagerFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllManagerFactory.class.getName());
    }

    /**
     * TrustManagerFactory SPI that returns X509TrustManagers which accept
     * any certificate chain without validation.
     */
    public static class TrustAllManagerFactory extends TrustManagerFactorySpi {

        public TrustAllManagerFactory() {
            // Public no-arg constructor required for Security framework reflection
        }

        @Override
        protected void engineInit(KeyStore ks) {
            // No-op: we trust everything regardless of keystore contents
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
            // No-op: we trust everything regardless of parameters
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
                        // Trust all client certificates
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all server certificates
                    }
                }
            };
        }
    }
}

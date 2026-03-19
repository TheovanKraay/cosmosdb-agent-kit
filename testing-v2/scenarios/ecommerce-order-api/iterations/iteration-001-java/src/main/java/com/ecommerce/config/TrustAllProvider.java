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
 * with trust-all implementations. This is required for the Cosmos DB Emulator
 * because its self-signed certificate uses SHA1withRSA which Java 17 rejects
 * during PKIX validation even when the cert is imported into the truststore.
 *
 * Reactor Netty (used by the Cosmos SDK) creates its own TrustManagerFactory
 * via TrustManagerFactory.getInstance("PKIX"), bypassing SSLContext.setDefault().
 * Registering this provider at position 1 ensures Netty picks up our trust-all
 * TrustManagerFactory instead.
 *
 * NOT FOR PRODUCTION USE — emulator development only.
 */
public class TrustAllProvider extends Provider {

    private static final String NAME = "TrustAllProvider";

    public TrustAllProvider() {
        super(NAME, "1.0", "Trust-all TrustManagerFactory for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactorySpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactorySpi.class.getName());
    }

    /**
     * Registers this provider at position 1 so it takes precedence over
     * the default SunJSSE provider. Call in main() before Spring starts.
     */
    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static class TrustAllTrustManagerFactorySpi extends TrustManagerFactorySpi {

        private final TrustManager[] trustManagers = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        @Override
        protected void engineInit(KeyStore ks) {}

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {}

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return trustManagers;
        }
    }
}

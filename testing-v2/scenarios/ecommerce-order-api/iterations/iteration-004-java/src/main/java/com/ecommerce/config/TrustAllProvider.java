package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

/**
 * Custom security provider that trusts all SSL certificates.
 * Required for connecting to the Cosmos DB Emulator which uses a self-signed certificate.
 * This must be installed BEFORE SpringApplication.run() is called.
 */
public class TrustAllProvider extends Provider {

    private static final String PROVIDER_NAME = "TrustAllProvider";

    @SuppressWarnings("deprecation")
    public TrustAllProvider() {
        super(PROVIDER_NAME, 1.0, "Trust-all TrustManagerFactory provider");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactory.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static class TrustAllTrustManagerFactory extends TrustManagerFactorySpi {

        @Override
        protected void engineInit(KeyStore ks) throws KeyStoreException {
            // No-op
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
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

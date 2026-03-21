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
 * Custom security provider that trusts all certificates.
 * Required for connecting to the Cosmos DB emulator which uses self-signed certs.
 */
public class TrustAllProvider extends Provider {

    private static final String NAME = "TrustAllProvider";
    private static final double VERSION = 1.0;
    private static final String INFO = "Trust-all TrustManagerFactory provider for Cosmos DB emulator";

    public TrustAllProvider() {
        super(NAME, VERSION, INFO);
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactorySpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactorySpi.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static class TrustAllTrustManagerFactorySpi extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) {
            // no-op
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
            // no-op
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // trust all
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

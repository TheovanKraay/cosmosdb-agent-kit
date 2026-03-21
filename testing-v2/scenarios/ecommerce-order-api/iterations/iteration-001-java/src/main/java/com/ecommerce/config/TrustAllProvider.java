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
 * Custom Java Security Provider that trusts all certificates.
 * Required for connecting to the Cosmos DB emulator which uses self-signed certs.
 */
public class TrustAllProvider extends Provider {

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        @Override
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    @SuppressWarnings("serial")
    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust-all TLS provider for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllFactory.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static class TrustAllFactory extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) { }
        @Override
        protected void engineInit(ManagerFactoryParameters spec) { }
        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{ TRUST_ALL };
        }
    }
}

package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

public class TrustAllProvider extends Provider {

    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    };

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust all certificates for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactory.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static X509TrustManager getTrustManager() {
        return TRUST_ALL_MANAGER;
    }

    public static class TrustAllTrustManagerFactory extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) {}

        @Override
        protected void engineInit(ManagerFactoryParameters spec) {}

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{ TRUST_ALL_MANAGER };
        }
    }
}

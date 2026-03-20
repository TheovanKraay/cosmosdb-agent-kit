package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

public class TrustAllProvider extends Provider {

    private static final String NAME = "TrustAllProvider";

    public TrustAllProvider() {
        super(NAME, "1.0", "Trust-all TrustManagerFactory provider for Cosmos DB Emulator");
        put("TrustManagerFactory.PKIX", TrustAllTrustManagerFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTrustManagerFactory.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new TrustAllX509TrustManager()}, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install TrustAllProvider", e);
        }
    }

    public static class TrustAllTrustManagerFactory extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) { }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) { }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{new TrustAllX509TrustManager()};
        }
    }

    private static class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) { }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) { }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}

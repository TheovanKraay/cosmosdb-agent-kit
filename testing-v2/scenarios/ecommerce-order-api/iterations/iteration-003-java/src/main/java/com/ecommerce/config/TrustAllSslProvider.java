package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;

public class TrustAllSslProvider extends Provider {

    public TrustAllSslProvider() {
        super("TrustAllSslProvider", "1.0", "Trust all certificates");
        put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTmfSpi.class.getName());
    }

    public static class TrustAllTmfSpi extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) { }

        @Override
        protected void engineInit(ManagerFactoryParameters spec) { }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };
        }
    }
}

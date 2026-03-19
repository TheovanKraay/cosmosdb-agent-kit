package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;

public class TrustAllTmfSpi extends TrustManagerFactorySpi {

    public static final Provider PROVIDER = new Provider("TrustAll", "1.0", "Trust-all TrustManagerFactory") {
        {
            put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
            put("TrustManagerFactory.SunX509", TrustAllTmfSpi.class.getName());
        }
    };

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

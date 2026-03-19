package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;

public class TrustAllTmfSpi extends TrustManagerFactorySpi {
    @Override
    protected void engineInit(KeyStore keyStore) throws KeyStoreException {
    }

    @Override
    protected void engineInit(ManagerFactoryParameters params)
            throws InvalidAlgorithmParameterException {
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            }
        };
    }
}

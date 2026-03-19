package com.ecommerce.config;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;

public class TrustAllTmfSpi extends TrustManagerFactorySpi {

    @Override
    protected void engineInit(KeyStore ks) throws KeyStoreException {
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
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
    }
}

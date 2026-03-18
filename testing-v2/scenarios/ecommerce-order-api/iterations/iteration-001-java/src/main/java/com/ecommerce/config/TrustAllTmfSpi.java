package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * TrustManagerFactory SPI that trusts all certificates.
 * Used for the Cosmos DB Emulator's self-signed certificate
 * which fails standard PKIX validation in Java 17+.
 */
public class TrustAllTmfSpi extends TrustManagerFactorySpi {

    public TrustAllTmfSpi() { }

    @Override
    protected void engineInit(KeyStore ks) { }

    @Override
    protected void engineInit(ManagerFactoryParameters spec) { }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] chain, String authType) { }

                public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            }
        };
    }
}

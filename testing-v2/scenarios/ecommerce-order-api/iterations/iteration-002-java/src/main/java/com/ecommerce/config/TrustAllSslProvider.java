package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;

/**
 * JCA Security Provider that installs a trust-all TrustManagerFactory.
 *
 * This is needed when running against the Azure Cosmos DB emulator whose
 * self-signed certificate is not trusted by the default Java trust store.
 * Only activated when COSMOS_ENDPOINT contains "localhost".
 */
public class TrustAllSslProvider extends Provider {

    public TrustAllSslProvider() {
        super("TrustAllSslProvider", "1.0", "Trust-all SSL provider for Cosmos emulator");
        put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTmfSpi.class.getName());
    }

    public static class TrustAllTmfSpi extends TrustManagerFactorySpi {

        private static final TrustManager TRUST_ALL = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };

        @Override protected void engineInit(KeyStore keyStore) {}
        @Override protected void engineInit(ManagerFactoryParameters spec) {}
        @Override protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{TRUST_ALL};
        }
    }
}

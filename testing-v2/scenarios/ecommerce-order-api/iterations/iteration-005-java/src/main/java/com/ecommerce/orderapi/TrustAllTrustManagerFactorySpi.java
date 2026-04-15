package com.ecommerce.orderapi;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;

/**
 * Trust-all TrustManagerFactory implementation for the Cosmos DB Emulator.
 *
 * The emulator's self-signed certificate may have an invalid signature that
 * fails PKIX path validation even after importing into the JDK truststore.
 * This implementation bypasses all certificate validation.
 *
 * Registered via a custom security Provider in Application.main() so that
 * Netty's JDK SSL context picks it up when calling
 * TrustManagerFactory.getInstance("PKIX").
 *
 * Only used for local development with the emulator — never in production.
 */
public class TrustAllTrustManagerFactorySpi extends TrustManagerFactorySpi {

    @Override
    protected void engineInit(KeyStore ks) throws KeyStoreException {
        // No-op: trust all certificates regardless of truststore contents
    }

    @Override
    protected void engineInit(ManagerFactoryParameters spec)
            throws InvalidAlgorithmParameterException {
        // No-op: trust all certificates regardless of parameters
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Trust all client certificates
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Trust all server certificates
                }
            }
        };
    }
}

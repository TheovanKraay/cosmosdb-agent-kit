package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Installs a trust-all JCA provider so the Cosmos DB Java SDK (via Reactor Netty
 * with JDK SSL mode) accepts the emulator's self-signed certificate.
 *
 * Problem: Java 17 + Cosmos DB Emulator - even after importing the emulator cert
 * into the JDK truststore via keytool, the PKIX validation fails with
 * "signature check failed". This happens because the emulator certificate uses
 * a signature algorithm that Java 17's security policy restricts.
 *
 * Fix: Install a custom JCA Provider at priority 1 that overrides the built-in
 * TrustManagerFactory implementations (PKIX, X509, SunX509) with a trust-all
 * implementation. When the Cosmos SDK calls TrustManagerFactory.getInstance(),
 * it gets our trust-all implementation instead of the restrictive built-in one.
 *
 * This is safe for local development with the emulator. In production,
 * the emulator cert issue does not apply.
 */
public class TrustAllSslConfig {

    private static volatile boolean installed = false;

    /**
     * Install the trust-all JCA provider at the highest priority (position 1).
     * Idempotent - safe to call multiple times.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        Security.insertProviderAt(new TrustAllProvider(), 1);
        installed = true;
    }

    /**
     * Custom JCA Provider that overrides TrustManagerFactory implementations
     * with trust-all versions that accept any certificate.
     */
    public static final class TrustAllProvider extends Provider {
        @SuppressWarnings("deprecation")
        public TrustAllProvider() {
            super("TrustAllProvider", "1.0",
                    "Trust-all SSL provider for Cosmos DB Emulator (development only)");
            // Override all common TrustManagerFactory algorithm names
            put("TrustManagerFactory.PKIX",
                    TrustAllTrustManagerFactorySpi.class.getName());
            put("TrustManagerFactory.X509",
                    TrustAllTrustManagerFactorySpi.class.getName());
            put("TrustManagerFactory.SunX509",
                    TrustAllTrustManagerFactorySpi.class.getName());
        }
    }

    /**
     * TrustManagerFactorySpi that always returns a trust-all TrustManager.
     */
    public static final class TrustAllTrustManagerFactorySpi extends TrustManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks) throws KeyStoreException {
            // No initialization needed
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            // No initialization needed
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{new TrustAllX509TrustManager()};
        }
    }

    /**
     * X509TrustManager that accepts all certificates without validation.
     * Only used for the Cosmos DB Emulator in development.
     */
    public static final class TrustAllX509TrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            // Accept all client certificates
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            // Accept all server certificates (emulator self-signed cert)
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}

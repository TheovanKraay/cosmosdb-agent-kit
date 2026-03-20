package com.ecommerce.config;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;

/**
 * Custom Security Provider that overrides PKIX and SunX509 TrustManagerFactory
 * to trust all certificates. Required for Java Cosmos SDK with the emulator
 * because Reactor Netty creates its own TrustManagerFactory, bypassing
 * the JVM default SSLContext.
 */
public class TrustAllProvider extends Provider {

    private static final TrustManager[] TRUST_ALL = new TrustManager[]{
        new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }
    };

    public TrustAllProvider() {
        super("TrustAll", "1.0", "Trust all certificates");
        put("TrustManagerFactory.PKIX", TrustAllFactory.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllFactory.class.getName());
    }

    public static void install() {
        Security.insertProviderAt(new TrustAllProvider(), 1);
    }

    public static class TrustAllFactory extends TrustManagerFactorySpi {
        @Override protected void engineInit(KeyStore ks) {}
        @Override protected void engineInit(ManagerFactoryParameters spec) {}
        @Override protected TrustManager[] engineGetTrustManagers() {
            return TRUST_ALL;
        }
    }
}

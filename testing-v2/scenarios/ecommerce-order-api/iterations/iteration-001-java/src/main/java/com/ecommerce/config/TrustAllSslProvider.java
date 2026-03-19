package com.ecommerce.config;

import java.security.Provider;

public class TrustAllSslProvider extends Provider {
    public TrustAllSslProvider() {
        super("TrustAllSslProvider", "1.0", "Trust all SSL certificates for Cosmos DB emulator");
        put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
    }
}

package com.ecommerce.config;

import java.security.Provider;

public class TrustAllSslProvider extends Provider {

    public TrustAllSslProvider() {
        super("TrustAllSSL", "1.0", "Trust-all TrustManagerFactory provider");
        put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTmfSpi.class.getName());
    }
}

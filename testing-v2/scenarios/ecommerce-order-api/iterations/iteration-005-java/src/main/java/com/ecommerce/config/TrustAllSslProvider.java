package com.ecommerce.config;

import javax.net.ssl.TrustManagerFactory;
import java.security.Provider;

public class TrustAllSslProvider extends Provider {
    public TrustAllSslProvider() {
        super("TrustAllSSL", "1.0", "Trust all SSL certificates");
        put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
        put("TrustManagerFactory.SunX509", TrustAllTmfSpi.class.getName());
    }
}

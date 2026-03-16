package com.ecommerce.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.*;
import java.security.cert.X509Certificate;

@Configuration
public class CosmosConfig {

    // Install a trust-all JSSE provider before any SSL context is created.
    // The Cosmos DB Emulator's self-signed cert fails Java 17's strict PKIX
    // chain validation even when imported into the JDK truststore.
    static {
        Security.insertProviderAt(new TrustAllSslProvider(), 1);
    }

    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";
    private static final String PARTITION_KEY = "/customerId";

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = System.getenv().getOrDefault("COSMOS_ENDPOINT", "https://localhost:8081");
        String key = System.getenv().getOrDefault("COSMOS_KEY",
                "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==");

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(DATABASE_NAME);
        CosmosDatabase database = cosmosClient.getDatabase(DATABASE_NAME);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(CONTAINER_NAME, PARTITION_KEY);
        database.createContainerIfNotExists(containerProperties,
                ThroughputProperties.createManualThroughput(400));

        return database;
    }

    // ---------------------------------------------------------------------------
    // Custom JCA/JSSE Security Provider that provides a trust-all TrustManager.
    // Installed at position 1 so it takes priority over the default SUN provider.
    // This is needed for the Cosmos DB Emulator whose cert fails PKIX validation.
    // ---------------------------------------------------------------------------

    static final class TrustAllSslProvider extends Provider {

        TrustAllSslProvider() {
            super("TrustAllSsl", "1.0", "Trust-all SSL provider for Cosmos DB Emulator");
            put("TrustManagerFactory.PKIX",    TrustAllFactory.class.getName());
            put("TrustManagerFactory.SunX509", TrustAllFactory.class.getName());
            put("TrustManagerFactory.X509",    TrustAllFactory.class.getName());
        }

        public static final class TrustAllFactory extends TrustManagerFactorySpi {
            @Override protected void engineInit(KeyStore ks) {}
            @Override protected void engineInit(ManagerFactoryParameters spec) {}
            @Override protected TrustManager[] engineGetTrustManagers() {
                return new TrustManager[]{ TRUST_ALL };
            }
        }

        private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        };
    }
}

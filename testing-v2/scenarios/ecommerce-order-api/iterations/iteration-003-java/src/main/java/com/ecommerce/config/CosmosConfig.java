package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;

@Configuration
public class CosmosConfig {

    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";

    static {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sslContext);
            Security.insertProviderAt(new TrustAllSslProvider(), 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure SSL for Cosmos DB emulator", e);
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = System.getenv("COSMOS_ENDPOINT");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = "https://localhost:8081";
        }
        String key = System.getenv("COSMOS_KEY");
        if (key == null || key.isEmpty()) {
            key = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
        }

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(DATABASE_NAME);
        return cosmosClient.getDatabase(DATABASE_NAME);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties containerProperties = new CosmosContainerProperties(
                CONTAINER_NAME, "/customerId");
        cosmosDatabase.createContainerIfNotExists(containerProperties,
                ThroughputProperties.createManualThroughput(400));
        return cosmosDatabase.getContainer(CONTAINER_NAME);
    }
}

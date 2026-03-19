package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;

@Configuration
public class CosmosConfig {

    private static final String DEFAULT_ENDPOINT = "https://localhost:8081";
    private static final String DEFAULT_KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";

    static {
        String endpoint = System.getenv("COSMOS_ENDPOINT");
        if (endpoint == null) endpoint = DEFAULT_ENDPOINT;
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            try {
                Security.insertProviderAt(new TrustAllSslProvider(), 1);
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new TrustManager[]{
                        new X509TrustManager() {
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        }
                }, new java.security.SecureRandom());
                SSLContext.setDefault(sc);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize trust-all SSL context", e);
            }
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = System.getenv("COSMOS_ENDPOINT");
        String key = System.getenv("COSMOS_KEY");
        if (endpoint == null || endpoint.isEmpty()) endpoint = DEFAULT_ENDPOINT;
        if (key == null || key.isEmpty()) key = DEFAULT_KEY;

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
        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(CONTAINER_NAME, "/customerId");
        cosmosDatabase.createContainerIfNotExists(containerProperties,
                ThroughputProperties.createManualThroughput(400));
        return cosmosDatabase.getContainer(CONTAINER_NAME);
    }
}

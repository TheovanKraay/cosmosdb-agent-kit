package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.ExcludedPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CosmosConfig {

    private static final String DEFAULT_ENDPOINT = "https://localhost:8081";
    private static final String DEFAULT_KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";

    static {
        String endpoint = System.getenv("COSMOS_ENDPOINT") != null
                ? System.getenv("COSMOS_ENDPOINT") : DEFAULT_ENDPOINT;
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            try {
                Security.insertProviderAt(new TrustAllSslProvider(), 1);
                TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAll, new java.security.SecureRandom());
                SSLContext.setDefault(sc);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL trust-all", e);
            }
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = System.getenv("COSMOS_ENDPOINT") != null
                ? System.getenv("COSMOS_ENDPOINT") : DEFAULT_ENDPOINT;
        String key = System.getenv("COSMOS_KEY") != null
                ? System.getenv("COSMOS_KEY") : DEFAULT_KEY;

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(DATABASE_NAME,
                ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosClient.getDatabase(DATABASE_NAME);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        partitionKeyDef.setPaths(Collections.singletonList("/customerId"));

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/customerId/?"),
                new IncludedPath("/status/?"),
                new IncludedPath("/createdAt/?"),
                new IncludedPath("/orderId/?")
        ));
        indexingPolicy.setExcludedPaths(Collections.singletonList(
                new ExcludedPath("/*")
        ));

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(CONTAINER_NAME, partitionKeyDef);
        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(containerProperties);
        return cosmosDatabase.getContainer(CONTAINER_NAME);
    }
}

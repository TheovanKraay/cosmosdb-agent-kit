package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class CosmosConfig {

    private static final String DEFAULT_ENDPOINT = "https://localhost:8081";
    private static final String DEFAULT_KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";

    private CosmosAsyncClient cosmosClient;

    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        String endpoint = System.getenv("COSMOS_ENDPOINT") != null
                ? System.getenv("COSMOS_ENDPOINT") : DEFAULT_ENDPOINT;
        String key = System.getenv("COSMOS_KEY") != null
                ? System.getenv("COSMOS_KEY") : DEFAULT_KEY;

        cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildAsyncClient();

        return cosmosClient;
    }

    @Bean
    public CosmosAsyncDatabase cosmosDatabase(CosmosAsyncClient client) {
        client.createDatabaseIfNotExists(DATABASE_NAME,
                ThroughputProperties.createAutoscaledThroughput(1000)).block();
        return client.getDatabase(DATABASE_NAME);
    }

    @Bean
    public CosmosAsyncContainer ordersContainer(CosmosAsyncDatabase database) {
        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(CONTAINER_NAME, "/customerId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/customerId/?"),
                new IncludedPath("/status/?"),
                new IncludedPath("/createdAt/?"),
                new IncludedPath("/orderId/?"),
                new IncludedPath("/total/?")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/\"_etag\"/?"),
                new ExcludedPath("/*")
        ));

        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtPathAsc = new CompositePath();
        createdAtPathAsc.setPath("/createdAt");
        createdAtPathAsc.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath customerPath = new CompositePath();
        customerPath.setPath("/customerId");
        customerPath.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtPathDesc = new CompositePath();
        createdAtPathDesc.setPath("/createdAt");
        createdAtPathDesc.setOrder(CompositePathSortOrder.DESCENDING);

        indexingPolicy.setCompositeIndexes(Arrays.asList(
                Arrays.asList(statusPath, createdAtPathAsc),
                Arrays.asList(customerPath, createdAtPathDesc)
        ));

        containerProperties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(containerProperties).block();
        return database.getContainer(CONTAINER_NAME);
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
        }
    }
}

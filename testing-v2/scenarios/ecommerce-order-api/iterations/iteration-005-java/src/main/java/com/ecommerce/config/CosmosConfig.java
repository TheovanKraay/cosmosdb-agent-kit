package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CosmosConfig {

    @Value("${COSMOS_ENDPOINT:https://localhost:8081}")
    private String endpoint;

    @Value("${COSMOS_KEY:C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==}")
    private String key;

    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .gatewayMode();

        return builder.buildClient();
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

        // Indexing policy with root path included (mandatory)
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Collections.singletonList(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(Collections.singletonList(new ExcludedPath("/\"_etag\"/?")));

        // Composite index for status + createdAt queries
        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtPath = new CompositePath();
        createdAtPath.setPath("/createdAt");
        createdAtPath.setOrder(CompositePathSortOrder.ASCENDING);

        indexingPolicy.setCompositeIndexes(
                Collections.singletonList(Arrays.asList(statusPath, createdAtPath)));

        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(containerProperties);
        return cosmosDatabase.getContainer(CONTAINER_NAME);
    }
}

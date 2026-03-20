package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    private CosmosClient cosmosClient;

    @Bean
    public CosmosClient cosmosClient() {
        cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
        return cosmosClient;
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient client) {
        CosmosDatabaseResponse response = client.createDatabaseIfNotExists(databaseName);
        return client.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase database) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setIncludedPaths(List.of(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(List.of(new ExcludedPath("/\"_etag\"/?")));

        // Composite index for status + createdAt queries
        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtPath = new CompositePath();
        createdAtPath.setPath("/createdAt");
        createdAtPath.setOrder(CompositePathSortOrder.DESCENDING);

        indexingPolicy.setCompositeIndexes(List.of(Arrays.asList(statusPath, createdAtPath)));

        CosmosContainerProperties containerProperties = new CosmosContainerProperties("orders", "/customerId");
        containerProperties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(containerProperties, ThroughputProperties.createManualThroughput(400));
        return database.getContainer("orders");
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
        }
    }
}

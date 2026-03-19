package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    private CosmosAsyncClient client;

    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .gatewayMode(new GatewayConnectionConfig())
                .buildAsyncClient();
        return client;
    }

    @Bean
    public CosmosAsyncDatabase cosmosAsyncDatabase(CosmosAsyncClient cosmosAsyncClient) {
        CosmosDatabaseResponse response = cosmosAsyncClient
                .createDatabaseIfNotExists(databaseName, ThroughputProperties.createManualThroughput(400))
                .block();
        return cosmosAsyncClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosAsyncContainer cosmosAsyncContainer(CosmosAsyncDatabase database) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setAutomatic(true);

        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/*"));
        indexingPolicy.setIncludedPaths(includedPaths);

        List<ExcludedPath> excludedPaths = new ArrayList<>();
        excludedPaths.add(new ExcludedPath("/\"_etag\"/?"));
        indexingPolicy.setExcludedPaths(excludedPaths);

        // Composite indexes for efficient queries
        List<List<CompositePath>> compositeIndexes = new ArrayList<>();

        // status + createdAt for querying by status sorted by date
        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtAsc = new CompositePath();
        createdAtAsc.setPath("/createdAt");
        createdAtAsc.setOrder(CompositePathSortOrder.ASCENDING);
        compositeIndexes.add(Arrays.asList(statusPath, createdAtAsc));

        CompositePath statusPath2 = new CompositePath();
        statusPath2.setPath("/status");
        statusPath2.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtDesc = new CompositePath();
        createdAtDesc.setPath("/createdAt");
        createdAtDesc.setOrder(CompositePathSortOrder.DESCENDING);
        compositeIndexes.add(Arrays.asList(statusPath2, createdAtDesc));

        // customerId + createdAt for customer order history
        CompositePath customerIdPath = new CompositePath();
        customerIdPath.setPath("/customerId");
        customerIdPath.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtAsc2 = new CompositePath();
        createdAtAsc2.setPath("/createdAt");
        createdAtAsc2.setOrder(CompositePathSortOrder.ASCENDING);
        compositeIndexes.add(Arrays.asList(customerIdPath, createdAtAsc2));

        CompositePath customerIdPath2 = new CompositePath();
        customerIdPath2.setPath("/customerId");
        customerIdPath2.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtDesc2 = new CompositePath();
        createdAtDesc2.setPath("/createdAt");
        createdAtDesc2.setOrder(CompositePathSortOrder.DESCENDING);
        compositeIndexes.add(Arrays.asList(customerIdPath2, createdAtDesc2));

        indexingPolicy.setCompositeIndexes(compositeIndexes);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("orders", "/customerId");
        containerProperties.setIndexingPolicy(indexingPolicy);

        CosmosContainerResponse response = database
                .createContainerIfNotExists(containerProperties)
                .block();

        return database.getContainer("orders");
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
    }
}

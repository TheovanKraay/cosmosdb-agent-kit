package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
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
        client.createDatabaseIfNotExists(databaseName, ThroughputProperties.createAutoscaledThroughput(1000));
        return client.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase database) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setAutomatic(true);

        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/customerId/?"));
        includedPaths.add(new IncludedPath("/status/?"));
        includedPaths.add(new IncludedPath("/createdAt/?"));
        includedPaths.add(new IncludedPath("/orderId/?"));
        indexingPolicy.setIncludedPaths(includedPaths);

        List<ExcludedPath> excludedPaths = new ArrayList<>();
        excludedPaths.add(new ExcludedPath("/items/*"));
        excludedPaths.add(new ExcludedPath("/shippingAddress/?"));
        indexingPolicy.setExcludedPaths(excludedPaths);

        // Composite indexes for efficient sorting
        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtPath = new CompositePath();
        createdAtPath.setPath("/createdAt");
        createdAtPath.setOrder(CompositePathSortOrder.DESCENDING);

        CompositePath customerIdPath = new CompositePath();
        customerIdPath.setPath("/customerId");
        customerIdPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtDescForCustomer = new CompositePath();
        createdAtDescForCustomer.setPath("/createdAt");
        createdAtDescForCustomer.setOrder(CompositePathSortOrder.DESCENDING);

        List<List<CompositePath>> compositeIndexes = new ArrayList<>();
        compositeIndexes.add(Arrays.asList(statusPath, createdAtPath));
        compositeIndexes.add(Arrays.asList(customerIdPath, createdAtDescForCustomer));
        indexingPolicy.setCompositeIndexes(compositeIndexes);

        CosmosContainerProperties containerProperties = new CosmosContainerProperties("orders", "/customerId");
        containerProperties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(containerProperties);
        return database.getContainer("orders");
    }
}

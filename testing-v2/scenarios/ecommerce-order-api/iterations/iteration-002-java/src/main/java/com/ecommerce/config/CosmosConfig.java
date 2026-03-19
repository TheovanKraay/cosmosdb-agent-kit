package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
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

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        CosmosDatabaseResponse response = cosmosClient.createDatabaseIfNotExists(
                databaseName, ThroughputProperties.createAutoscaledThroughput(1000));
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase cosmosDatabase) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/*")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/items/*"),
                new ExcludedPath("/\"_etag\"/?")
        ));

        List<CompositePath> composite1 = new ArrayList<>();
        CompositePath cp1a = new CompositePath();
        cp1a.setPath("/status");
        cp1a.setOrder(CompositePathSortOrder.ASCENDING);
        composite1.add(cp1a);
        CompositePath cp1b = new CompositePath();
        cp1b.setPath("/createdAt");
        cp1b.setOrder(CompositePathSortOrder.DESCENDING);
        composite1.add(cp1b);

        List<CompositePath> composite2 = new ArrayList<>();
        CompositePath cp2a = new CompositePath();
        cp2a.setPath("/customerId");
        cp2a.setOrder(CompositePathSortOrder.ASCENDING);
        composite2.add(cp2a);
        CompositePath cp2b = new CompositePath();
        cp2b.setPath("/createdAt");
        cp2b.setOrder(CompositePathSortOrder.DESCENDING);
        composite2.add(cp2b);

        indexingPolicy.setCompositeIndexes(Arrays.asList(composite1, composite2));

        CosmosContainerProperties containerProperties = new CosmosContainerProperties("orders", "/customerId");
        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(containerProperties);
        return cosmosDatabase.getContainer("orders");
    }
}

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

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient client) {
        client.createDatabaseIfNotExists(databaseName, ThroughputProperties.createAutoscaledThroughput(1000));
        return client.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase database) {
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/*")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/items/*"),
                new ExcludedPath("/shippingAddress/?"),
                new ExcludedPath("/_etag/?")
        ));

        CompositePath statusAsc = new CompositePath();
        statusAsc.setPath("/status");
        statusAsc.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtAsc = new CompositePath();
        createdAtAsc.setPath("/createdAt");
        createdAtAsc.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtDesc = new CompositePath();
        createdAtDesc.setPath("/createdAt");
        createdAtDesc.setOrder(CompositePathSortOrder.DESCENDING);

        CompositePath customerIdAsc = new CompositePath();
        customerIdAsc.setPath("/customerId");
        customerIdAsc.setOrder(CompositePathSortOrder.ASCENDING);

        List<List<CompositePath>> compositeIndexes = new ArrayList<>();
        compositeIndexes.add(Arrays.asList(statusAsc, createdAtDesc));
        compositeIndexes.add(Arrays.asList(customerIdAsc, createdAtDesc));

        indexingPolicy.setCompositeIndexes(compositeIndexes);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("orders", "/customerId");
        containerProperties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(containerProperties);
        return database.getContainer("orders");
    }
}

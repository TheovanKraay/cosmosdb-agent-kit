package com.multitenant.config;

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
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKeyDefinitionVersion;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CosmosDbConfiguration {

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true);

        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            builder.gatewayMode();
        } else {
            builder.directMode();
        }

        return builder.buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer multitenantContainer(CosmosDatabase cosmosDatabase) {
        // Hierarchical partition key: /tenantId + /type
        PartitionKeyDefinition pkDefinition = new PartitionKeyDefinition();
        pkDefinition.setPaths(Arrays.asList("/tenantId", "/type"));
        pkDefinition.setVersion(PartitionKeyDefinitionVersion.V2);
        pkDefinition.setKind(PartitionKind.MULTI_HASH);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("multitenant-data", pkDefinition);

        // Custom indexing policy
        IndexingPolicy indexingPolicy = new IndexingPolicy();

        // Included paths
        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/*"));
        indexingPolicy.setIncludedPaths(includedPaths);

        // Excluded paths - exclude large/unused fields
        List<ExcludedPath> excludedPaths = new ArrayList<>();
        excludedPaths.add(new ExcludedPath("/\"_etag\"/?"));
        excludedPaths.add(new ExcludedPath("/description/?"));
        excludedPaths.add(new ExcludedPath("/email/?"));
        indexingPolicy.setExcludedPaths(excludedPaths);

        // Composite indexes for common queries
        List<List<CompositePath>> compositeIndexes = new ArrayList<>();

        // Index for querying tasks by status within a tenant
        List<CompositePath> statusIndex = new ArrayList<>();
        statusIndex.add(new CompositePath().setPath("/tenantId").setOrder(CompositePathSortOrder.ASCENDING));
        statusIndex.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
        compositeIndexes.add(statusIndex);

        // Index for querying tasks by priority within a tenant
        List<CompositePath> priorityIndex = new ArrayList<>();
        priorityIndex.add(new CompositePath().setPath("/tenantId").setOrder(CompositePathSortOrder.ASCENDING));
        priorityIndex.add(new CompositePath().setPath("/priority").setOrder(CompositePathSortOrder.ASCENDING));
        compositeIndexes.add(priorityIndex);

        // Index for querying tasks by assignee within a tenant
        List<CompositePath> assigneeIndex = new ArrayList<>();
        assigneeIndex.add(new CompositePath().setPath("/tenantId").setOrder(CompositePathSortOrder.ASCENDING));
        assigneeIndex.add(new CompositePath().setPath("/assigneeId").setOrder(CompositePathSortOrder.ASCENDING));
        compositeIndexes.add(assigneeIndex);

        indexingPolicy.setCompositeIndexes(compositeIndexes);
        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(
                containerProperties,
                ThroughputProperties.createAutoscaledThroughput(4000));

        return cosmosDatabase.getContainer("multitenant-data");
    }
}

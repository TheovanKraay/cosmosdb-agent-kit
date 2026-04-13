package com.multitenant.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
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

/**
 * Cosmos DB configuration following best practices:
 * - Singleton CosmosClient (rule: sdk-singleton)
 * - Direct connection mode (rule: sdk-direct-mode)
 * - Hierarchical partition keys /tenantId + /type (rule: partition-hierarchical)
 * - Custom indexing policy with composite indexes (rule: index-composite)
 * - Excluded unused paths (rule: index-exclude-unused)
 */
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
                .directMode(DirectConnectionConfig.getDefaultConfig())
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        CosmosDatabaseResponse response = cosmosClient.createDatabaseIfNotExists(
                databaseName,
                ThroughputProperties.createAutoscaledThroughput(4000)
        );
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        // Hierarchical partition key: /tenantId + /type
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        partitionKeyDef.setPaths(Arrays.asList("/tenantId", "/type"));
        partitionKeyDef.setKind(PartitionKind.MULTI_HASH);
        partitionKeyDef.setVersion(PartitionKeyDefinitionVersion.V2);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("entities", partitionKeyDef);

        // Custom indexing policy
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setAutomatic(true);

        // Include root path
        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/*"));
        indexingPolicy.setIncludedPaths(includedPaths);

        // Exclude unused paths to save RU on writes
        List<ExcludedPath> excludedPaths = new ArrayList<>();
        excludedPaths.add(new ExcludedPath("/\"_etag\"/?"));
        excludedPaths.add(new ExcludedPath("/description/?"));
        excludedPaths.add(new ExcludedPath("/email/?"));
        indexingPolicy.setExcludedPaths(excludedPaths);

        // Composite indexes for common query patterns
        List<List<CompositePath>> compositeIndexes = new ArrayList<>();

        // Composite index for tasks by status + priority queries
        List<CompositePath> statusPriorityIndex = new ArrayList<>();
        statusPriorityIndex.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
        statusPriorityIndex.add(new CompositePath().setPath("/priority").setOrder(CompositePathSortOrder.ASCENDING));
        compositeIndexes.add(statusPriorityIndex);

        // Composite index for tasks by assigneeId + status
        List<CompositePath> assigneeStatusIndex = new ArrayList<>();
        assigneeStatusIndex.add(new CompositePath().setPath("/assigneeId").setOrder(CompositePathSortOrder.ASCENDING));
        assigneeStatusIndex.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
        compositeIndexes.add(assigneeStatusIndex);

        // Composite index for createdAt ordering with type
        List<CompositePath> typeCreatedIndex = new ArrayList<>();
        typeCreatedIndex.add(new CompositePath().setPath("/type").setOrder(CompositePathSortOrder.ASCENDING));
        typeCreatedIndex.add(new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.DESCENDING));
        compositeIndexes.add(typeCreatedIndex);

        indexingPolicy.setCompositeIndexes(compositeIndexes);
        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(containerProperties);
        return cosmosDatabase.getContainer("entities");
    }
}

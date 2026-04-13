package com.multitenant.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosClient;
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

import java.util.Arrays;
import java.util.List;

/**
 * Cosmos DB configuration using raw SDK (no Spring Data Cosmos).
 * Rule 4.12: Use dependent @Bean methods, not @PostConstruct.
 * Rule 4.8: Use Gateway mode for the Cosmos DB Emulator.
 * Rule 4.11: Enable contentResponseOnWriteEnabled(true).
 * Rule 4.22: Reuse CosmosClient as singleton @Bean.
 */
@Configuration
public class CosmosDbConfiguration {

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    /**
     * Rule 4.22: Singleton CosmosClient.
     * Rule 4.8: Use gatewayMode() for emulator compatibility.
     * Rule 4.11: Enable contentResponseOnWriteEnabled(true) so createItem returns the document.
     * Rule 4.12: Dependent @Bean for database initialization — creates database before containers.
     */
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        CosmosClient client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .gatewayMode()
                .buildClient();

        client.createDatabaseIfNotExists(databaseName,
                ThroughputProperties.createAutoscaledThroughput(4000));

        return client;
    }

    /**
     * Rule 4.12: Dependent @Bean — database depends on client.
     */
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        return cosmosClient.getDatabase(databaseName);
    }

    /**
     * Rule 2.3: Hierarchical partition keys (/tenantId + /type).
     * Rule 5.2: Composite indexes for ORDER BY queries.
     * Rule 5.3: Exclude unused index paths.
     * Rule 1.11: Type discriminator — single container with type field.
     */
    @Bean
    public CosmosContainer tenantsContainer(CosmosDatabase database) {
        // Hierarchical partition key: /tenantId + /type
        PartitionKeyDefinition pkDef = new PartitionKeyDefinition();
        pkDef.setPaths(Arrays.asList("/tenantId", "/type"));
        pkDef.setKind(PartitionKind.MULTI_HASH);
        pkDef.setVersion(PartitionKeyDefinitionVersion.V2);

        CosmosContainerProperties props = new CosmosContainerProperties("multitenant-data", pkDef);

        // Rule 5.3: Custom indexing policy with excluded paths
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(List.of(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/description/?"),
                new ExcludedPath("/\"_etag\"/?")
        ));

        // Rule 5.2: Composite indexes for multi-field queries
        // Rule 5.1: Composite index directions must match ORDER BY
        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtPath = new CompositePath();
        createdAtPath.setPath("/createdAt");
        createdAtPath.setOrder(CompositePathSortOrder.DESCENDING);

        CompositePath priorityPath = new CompositePath();
        priorityPath.setPath("/priority");
        priorityPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath titlePath = new CompositePath();
        titlePath.setPath("/title");
        titlePath.setOrder(CompositePathSortOrder.ASCENDING);

        indexingPolicy.setCompositeIndexes(Arrays.asList(
                Arrays.asList(statusPath, createdAtPath),
                Arrays.asList(priorityPath, titlePath)
        ));

        props.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(props);
        return database.getContainer("multitenant-data");
    }
}

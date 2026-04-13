package com.multitenant.saas.config;

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
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Cosmos DB configuration with best practices:
 * - Rule 4.16: Singleton CosmosClient
 * - Rule 4.4/4.6: Gateway mode for emulator, Direct for production
 * - Rule 4.9: contentResponseOnWriteEnabled(true)
 * - Rule 2.3: Hierarchical partition keys (/tenantId, /type)
 * - Rule 5.2: Composite indexes for multi-field queries
 * - Rule 5.1: Custom indexing policy with excluded paths
 */
@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    private CosmosClient cosmosClient;

    @Bean
    public CosmosClient cosmosClient() {
        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true);

        if (isEmulator) {
            builder.gatewayMode();
            logger.info("Using Gateway mode for Cosmos DB Emulator");
        } else {
            builder.directMode();
            logger.info("Using Direct mode for production Cosmos DB");
        }

        this.cosmosClient = builder.buildClient();
        return this.cosmosClient;
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        CosmosDatabase database = cosmosClient.getDatabase(databaseName);

        // Hierarchical partition keys: /tenantId (broad) → /type (narrow)
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        partitionKeyDef.setPaths(Arrays.asList("/tenantId", "/type"));
        partitionKeyDef.setKind(PartitionKind.MULTI_HASH);
        partitionKeyDef.setVersion(PartitionKeyDefinitionVersion.V2);

        CosmosContainerProperties containerProperties = new CosmosContainerProperties(
                "multitenant-container", partitionKeyDef);

        // Custom indexing policy
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setAutomatic(true);
        indexingPolicy.setIncludedPaths(Collections.singletonList(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/description/?"),
                new ExcludedPath("/\"_etag\"/?")
        ));

        // Composite indexes for common query patterns
        CompositePath typePath = new CompositePath();
        typePath.setPath("/type");
        typePath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath createdAtPath = new CompositePath();
        createdAtPath.setPath("/createdAt");
        createdAtPath.setOrder(CompositePathSortOrder.DESCENDING);

        CompositePath assigneePath = new CompositePath();
        assigneePath.setPath("/assigneeId");
        assigneePath.setOrder(CompositePathSortOrder.ASCENDING);

        CompositePath priorityPath = new CompositePath();
        priorityPath.setPath("/priority");
        priorityPath.setOrder(CompositePathSortOrder.ASCENDING);

        indexingPolicy.setCompositeIndexes(Arrays.asList(
                Arrays.asList(typePath, statusPath, createdAtPath),
                Arrays.asList(typePath, assigneePath),
                Arrays.asList(typePath, priorityPath)
        ));

        containerProperties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(
                containerProperties,
                ThroughputProperties.createAutoscaledThroughput(4000));

        logger.info("Initialized database '{}' with container 'multitenant-container' "
                + "using hierarchical partition keys [/tenantId, /type]", databaseName);

        return database;
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        return cosmosDatabase.getContainer("multitenant-container");
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
            logger.info("Cosmos DB client closed");
        }
    }
}

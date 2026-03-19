package com.example.multitenentsaas.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
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

/**
 * Cosmos DB configuration.
 *
 * Best practices applied:
 * - Rule 4.16: Singleton CosmosClient (registered as Spring bean)
 * - Rule 4.4:  Direct mode for production, Gateway for emulator
 * - Rule 4.6:  Gateway mode for emulator (Direct has SSL issues)
 * - Rule 4.9:  contentResponseOnWriteEnabled for Java SDK
 * - Rule 4.11: Local dev config to avoid cloud connection conflicts
 * - Rule 4.10: Spring Boot 3.2.1 requires Java 17+
 * - Rule 2.3:  Hierarchical partition keys for multi-tenant flexibility
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

    @Value("${azure.cosmos.container}")
    private String containerName;

    private CosmosClient cosmosClient;

    /**
     * Creates a singleton CosmosClient bean (Rule 4.16).
     * Uses Gateway mode for emulator (Rule 4.6) and enables content response on writes (Rule 4.9).
     */
    @Bean
    public CosmosClient cosmosClient() {
        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true); // Rule 4.9: Java SDK returns null without this

        if (isEmulator) {
            // Rule 4.6: Gateway mode required for emulator
            builder.gatewayMode();
            logger.info("Using Gateway mode for Cosmos DB Emulator");
        } else {
            // Rule 4.4: Direct mode for production
            builder.directMode();
            logger.info("Using Direct mode for production Cosmos DB");
        }

        this.cosmosClient = builder.buildClient();
        return this.cosmosClient;
    }

    /**
     * Creates the database and container with hierarchical partition keys.
     * 
     * Rule 2.3: Hierarchical partition keys for multi-tenant pattern
     * - Level 1: /tenantId — isolates tenant data
     * - Level 2: /type    — separates entity types (tenant, user, project, task)
     * - Level 3: /projectId — further partitions tasks within projects
     * 
     * This supports:
     * - Queries scoped to a tenant (Level 1)
     * - Queries for a specific entity type within a tenant (Level 1+2)
     * - Queries for tasks within a specific project (Level 1+2+3)
     */
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        // Create database if not exists
        cosmosClient.createDatabaseIfNotExists(databaseName);

        CosmosDatabase database = cosmosClient.getDatabase(databaseName);

        // Rule 2.3: Hierarchical partition keys
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        partitionKeyDef.setPaths(Arrays.asList("/tenantId", "/type", "/projectId"));
        partitionKeyDef.setKind(PartitionKind.MULTI_HASH);
        partitionKeyDef.setVersion(PartitionKeyDefinitionVersion.V2);

        CosmosContainerProperties containerProperties = new CosmosContainerProperties(
                containerName, partitionKeyDef);

        // Rule 6.1: Autoscale for variable workloads
        database.createContainerIfNotExists(
                containerProperties,
                ThroughputProperties.createAutoscaledThroughput(4000));

        logger.info("Initialized Cosmos DB database '{}' with container '{}' using hierarchical partition keys",
                databaseName, containerName);

        return database;
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        return cosmosDatabase.getContainer(containerName);
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
            logger.info("Cosmos DB client closed");
        }
    }
}

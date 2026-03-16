package com.example.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cosmos DB configuration following AGENTS.md best practices:
 * - Rule 4.10: Use dependent @Bean methods (no @PostConstruct)
 * - Rule 4.18: Reuse CosmosClient as singleton
 * - Rule 4.9:  Enable contentResponseOnWriteEnabled
 * - Rule 4.6:  Use Gateway mode for emulator compatibility
 * - Rule 4.11: Spring Boot 3.x with Java 17
 */
@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Value("${cosmos.container}")
    private String containerName;

    /**
     * Singleton CosmosClient bean.
     * Uses Gateway mode for emulator compatibility (AGENTS.md rule 4.6).
     * Gateway mode works reliably with the Cosmos DB Emulator after
     * the emulator cert is imported into the JDK truststore.
     * destroyMethod = "close" ensures proper cleanup on shutdown.
     */
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        logger.info("Connecting to Cosmos DB at: {}", endpoint);

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()           // Required for emulator (AGENTS.md 4.6)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)  // Ensure created items are returned (AGENTS.md 4.9)
                .buildClient();
    }

    /**
     * CosmosDatabase bean — depends on cosmosClient.
     * Uses parameter injection (not @PostConstruct) to avoid circular dependency.
     * AGENTS.md rule 4.10.
     */
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    /**
     * CosmosContainer bean — depends on cosmosDatabase.
     * Container is partitioned by /customerId for efficient customer queries.
     * AGENTS.md rule 2.6 (Align Partition Key with Query Patterns).
     * Uses autoscale throughput for variable workloads (AGENTS.md rule 6.1).
     */
    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties(containerName, "/customerId");

        cosmosDatabase.createContainerIfNotExists(
                props,
                ThroughputProperties.createAutoscaledThroughput(4000)
        );

        return cosmosDatabase.getContainer(containerName);
    }
}

package com.multitenant.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKeyDefinitionVersion;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;

// Rule 4.12: Do not name class CosmosConfig to avoid collision with Spring Data Cosmos
// Rule 4.22: Reuse CosmosClient as Singleton — lazy init with retry for emulator readiness
@Component
public class CosmosDbConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDbConfig.class);
    private static final int MAX_INIT_ATTEMPTS = 10;

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    @Value("${azure.cosmos.container}")
    private String containerName;

    private CosmosClient cosmosClient;
    private CosmosContainer cosmosContainer;
    private volatile boolean initialized = false;

    public synchronized CosmosContainer getContainer() {
        if (!initialized) {
            initialize();
        }
        return cosmosContainer;
    }

    private void initialize() {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_INIT_ATTEMPTS; attempt++) {
            try {
                CosmosClientBuilder builder = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .key(key)
                        .consistencyLevel(ConsistencyLevel.SESSION)
                        // Rule 4.11: Enable content response on write to get documents back
                        .contentResponseOnWriteEnabled(true);

                // Rule 4.8: Use Gateway mode for emulator, Direct for production
                if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
                    builder.gatewayMode(GatewayConnectionConfig.getDefaultConfig());
                } else {
                    builder.directMode();
                }

                cosmosClient = builder.buildClient();

                // Create database and container
                cosmosClient.createDatabaseIfNotExists(databaseName);
                CosmosDatabase database = cosmosClient.getDatabase(databaseName);

                // Rule 2.3: Hierarchical partition keys for multi-tenant design
                PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
                partitionKeyDef.setPaths(Arrays.asList("/tenantId", "/type"));
                partitionKeyDef.setKind(PartitionKind.MULTI_HASH);
                partitionKeyDef.setVersion(PartitionKeyDefinitionVersion.V2);

                CosmosContainerProperties containerProperties =
                        new CosmosContainerProperties(containerName, partitionKeyDef);

                // Rule 5.3: Exclude unused index paths
                IndexingPolicy indexingPolicy = new IndexingPolicy();
                indexingPolicy.setIncludedPaths(Collections.singletonList(new IncludedPath("/*")));
                indexingPolicy.setExcludedPaths(Arrays.asList(
                        new ExcludedPath("/_etag/?"),
                        new ExcludedPath("/description/?")
                ));
                containerProperties.setIndexingPolicy(indexingPolicy);

                // Rule 6.1: Use autoscale for variable workloads
                database.createContainerIfNotExists(
                        containerProperties,
                        ThroughputProperties.createAutoscaledThroughput(4000));

                cosmosContainer = database.getContainer(containerName);
                initialized = true;
                logger.info("Cosmos DB initialized successfully on attempt {}", attempt);
                return;
            } catch (Exception e) {
                lastException = e;
                logger.warn("Cosmos DB init attempt {}/{} failed: {}", attempt, MAX_INIT_ATTEMPTS, e.getMessage());
                if (cosmosClient != null) {
                    try { cosmosClient.close(); } catch (Exception ignored) {}
                    cosmosClient = null;
                }
                if (attempt < MAX_INIT_ATTEMPTS) {
                    try {
                        Thread.sleep(attempt * 2000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during Cosmos DB initialization", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Failed to initialize Cosmos DB after " + MAX_INIT_ATTEMPTS + " attempts", lastException);
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
        }
    }
}

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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cosmos DB configuration with lazy initialization and retry.
 *
 * Uses @Component with synchronized getContainer() instead of @Bean chain
 * so the app starts immediately and connects to Cosmos DB on first request.
 * A background warmup thread eagerly initializes the connection after Spring
 * starts so the container is ready before the first API request arrives.
 *
 * Best practices:
 * - Rule 4.16: Singleton CosmosClient
 * - Rule 4.4/4.6: Gateway mode for emulator, Direct for production
 * - Rule 4.9: contentResponseOnWriteEnabled(true)
 * - Rule 2.3: Hierarchical partition keys (/tenantId, /type)
 * - Rule 5.2: Composite indexes for multi-field queries
 * - Rule 5.1: Custom indexing policy with excluded paths
 */
@Component
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 500;  // Short initial backoff for fast convergence in CI
    private static final long MAX_BACKOFF_MS = 5000;     // Cap retry wait to keep each inner cycle short

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            long warmupDeadline = System.currentTimeMillis() + 110_000; // 110s total budget (CI health timeout is 120s)
            logger.info("Starting Cosmos DB warmup in background (110s budget)...");
            while (System.currentTimeMillis() < warmupDeadline) {
                try {
                    getContainer();
                    ready = true;
                    logger.info("Cosmos DB warmup completed successfully");
                    return;
                } catch (Exception e) {
                    long remaining = (warmupDeadline - System.currentTimeMillis()) / 1000;
                    logger.warn("Cosmos DB warmup attempt failed (~{}s remaining): {}", remaining, e.getMessage());
                    if (System.currentTimeMillis() < warmupDeadline) {
                        try {
                            Thread.sleep(2000); // Brief pause before retrying the full init cycle
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
            logger.error("Cosmos DB warmup exhausted 110s budget — health will remain 503");
        }, "cosmos-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    /**
     * Returns true when the Cosmos DB container has been successfully initialized.
     * Used by the health endpoint to delay returning 200 until the container is ready,
     * so that the CI test harness (which waits for health=200 before running tests)
     * doesn't start tests before Cosmos DB is available.
     */
    public boolean isReady() {
        return ready;
    }

    public synchronized CosmosContainer getContainer() {
        if (container != null) {
            return container;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (cosmosClient == null) {
                    CosmosClientBuilder builder = new CosmosClientBuilder()
                            .endpoint(endpoint)
                            .key(key)
                            .consistencyLevel(ConsistencyLevel.SESSION)
                            .contentResponseOnWriteEnabled(true);

                    if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
                        builder.gatewayMode();
                        logger.info("Using Gateway mode for Cosmos DB Emulator");
                    } else {
                        builder.directMode();
                        logger.info("Using Direct mode for production Cosmos DB");
                    }

                    cosmosClient = builder.buildClient();
                }

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

                List<IncludedPath> includedPaths = new ArrayList<>();
                includedPaths.add(new IncludedPath("/*"));
                indexingPolicy.setIncludedPaths(includedPaths);

                List<ExcludedPath> excludedPaths = new ArrayList<>();
                excludedPaths.add(new ExcludedPath("/\"_etag\"/?"));
                excludedPaths.add(new ExcludedPath("/description/?"));
                excludedPaths.add(new ExcludedPath("/email/?"));
                indexingPolicy.setExcludedPaths(excludedPaths);

                // Composite indexes for common query patterns
                List<List<CompositePath>> compositeIndexes = new ArrayList<>();

                List<CompositePath> statusIndex = new ArrayList<>();
                statusIndex.add(new CompositePath().setPath("/type").setOrder(CompositePathSortOrder.ASCENDING));
                statusIndex.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
                statusIndex.add(new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.DESCENDING));
                compositeIndexes.add(statusIndex);

                List<CompositePath> assigneeIndex = new ArrayList<>();
                assigneeIndex.add(new CompositePath().setPath("/type").setOrder(CompositePathSortOrder.ASCENDING));
                assigneeIndex.add(new CompositePath().setPath("/assigneeId").setOrder(CompositePathSortOrder.ASCENDING));
                compositeIndexes.add(assigneeIndex);

                List<CompositePath> priorityIndex = new ArrayList<>();
                priorityIndex.add(new CompositePath().setPath("/type").setOrder(CompositePathSortOrder.ASCENDING));
                priorityIndex.add(new CompositePath().setPath("/priority").setOrder(CompositePathSortOrder.ASCENDING));
                compositeIndexes.add(priorityIndex);

                indexingPolicy.setCompositeIndexes(compositeIndexes);
                containerProperties.setIndexingPolicy(indexingPolicy);

                database.createContainerIfNotExists(
                        containerProperties,
                        ThroughputProperties.createAutoscaledThroughput(4000));

                container = database.getContainer("multitenant-container");
                logger.info("Cosmos DB container initialized on attempt {}", attempt);
                return container;

            } catch (Exception e) {
                logger.warn("Cosmos DB init attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                        Thread.sleep(Math.min(backoff, MAX_BACKOFF_MS));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during Cosmos DB initialization", ie);
                    }
                } else {
                    throw new RuntimeException("Failed to initialize Cosmos DB after " + MAX_RETRIES + " attempts", e);
                }
            }
        }
        throw new RuntimeException("Failed to initialize Cosmos DB");
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
            logger.info("Cosmos DB client closed");
        }
    }
}

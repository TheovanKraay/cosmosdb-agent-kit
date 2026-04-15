package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cosmos DB configuration with lazy initialization and background warmup.
 * Uses a daemon thread to initialize the connection without blocking startup,
 * allowing the health endpoint to gate on readiness.
 */
@Component
public class CosmosDbConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDbConfiguration.class);

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    @Value("${azure.cosmos.container}")
    private String containerName;

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Start background warmup thread to initialize Cosmos DB connection.
     * This avoids blocking Spring startup and allows the health endpoint
     * to report readiness only after the connection is established.
     */
    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(this::initialize);
        warmupThread.setDaemon(true);
        warmupThread.setName("cosmos-warmup");
        warmupThread.start();
    }

    private void initialize() {
        logger.info("Starting Cosmos DB initialization (endpoint: {})", endpoint);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Close any previous client attempt
                if (cosmosClient != null) {
                    try { cosmosClient.close(); } catch (Exception e) { /* ignore */ }
                    cosmosClient = null;
                }

                // Build client with gateway mode for emulator, direct mode for production
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

                cosmosClient = builder.buildClient();

                // Create database if not exists
                cosmosClient.createDatabaseIfNotExists(databaseName);
                CosmosDatabase database = cosmosClient.getDatabase(databaseName);

                // Create container with customerId as partition key
                CosmosContainerProperties containerProps = new CosmosContainerProperties(
                    containerName, "/customerId");

                // Configure indexing policy for efficient queries
                IndexingPolicy indexingPolicy = new IndexingPolicy();
                indexingPolicy.setIncludedPaths(Arrays.asList(
                    new IncludedPath("/*")
                ));
                indexingPolicy.setExcludedPaths(Arrays.asList(
                    new ExcludedPath("/\"_etag\"/?")
                ));

                // Composite indexes for status + createdAt queries (Rule 5.1, 5.2)
                CompositePath statusAsc = new CompositePath();
                statusAsc.setPath("/status");
                statusAsc.setOrder(CompositePathSortOrder.ASCENDING);

                CompositePath createdAtDesc = new CompositePath();
                createdAtDesc.setPath("/createdAt");
                createdAtDesc.setOrder(CompositePathSortOrder.DESCENDING);

                indexingPolicy.setCompositeIndexes(Collections.singletonList(
                    Arrays.asList(statusAsc, createdAtDesc)
                ));

                containerProps.setIndexingPolicy(indexingPolicy);

                database.createContainerIfNotExists(
                    containerProps,
                    ThroughputProperties.createAutoscaledThroughput(4000));

                container = database.getContainer(containerName);

                ready.set(true);
                logger.info("Cosmos DB initialized successfully (database: {}, container: {})",
                    databaseName, containerName);
                return;

            } catch (Exception e) {
                logger.warn("Cosmos DB initialization failed, retrying in 2s: {}", e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            try {
                cosmosClient.close();
            } catch (Exception e) {
                logger.warn("Error closing CosmosClient: {}", e.getMessage());
            }
        }
    }

    public CosmosContainer getContainer() {
        return container;
    }

    public boolean isReady() {
        return ready.get();
    }
}

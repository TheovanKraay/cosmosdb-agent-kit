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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cosmos DB configuration with lazy initialization and TWO-PHASE background warmup.
 *
 * Phase 1: Polls the Cosmos DB endpoint with lightweight HTTP GET every 2s until
 *          it returns any non-503 HTTP response (401 = emulator is running but
 *          requires auth). Only 503 and connection failures mean "still starting".
 *          A 60s fallback timeout ensures Phase 1 proceeds regardless.
 *
 * Phase 2: Only after the endpoint is reachable, calls buildClient() and
 *          createDatabaseIfNotExists()/createContainerIfNotExists().
 *
 * Health endpoint gates on isReady() flag — returns 503 until container is initialized.
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
     * Uses a daemon thread so the JVM can exit if Spring shuts down.
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

        // === PHASE 1: Wait for the endpoint to be reachable ===
        // Poll with lightweight HTTP GET to avoid SDK internal timeouts (~23s each).
        // The emulator can return 503/10001 for 60-120+ seconds during startup.
        waitForEndpoint();

        // === PHASE 2: Initialize SDK after endpoint is ready ===
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Close any previous client attempt for fresh state
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
                logger.warn("Cosmos DB SDK initialization failed, retrying in 1s: {}", e.getMessage());
                // Close failed client for fresh state on retry
                if (cosmosClient != null) {
                    try { cosmosClient.close(); } catch (Exception ex) { /* ignore */ }
                    cosmosClient = null;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Phase 1: Poll the Cosmos DB endpoint with a lightweight HTTP GET every 2s
     * until it returns any non-503 HTTP response. The emulator returns 401 for
     * unauthenticated requests when it IS ready — only 503 means "still starting".
     * A 60s fallback timeout ensures Phase 1 proceeds regardless.
     */
    private void waitForEndpoint() {
        long deadline = System.currentTimeMillis() + 60_000;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int status = conn.getResponseCode();
                conn.disconnect();

                if (status != 503) {
                    logger.info("Cosmos DB endpoint is reachable (HTTP {})", status);
                    return;
                }
                logger.info("Cosmos DB endpoint returned HTTP 503, waiting...");
            } catch (Exception e) {
                logger.info("Cosmos DB endpoint not ready: {}", e.getMessage());
            }

            if (System.currentTimeMillis() >= deadline) {
                logger.warn("Phase 1 fallback timeout (60s) reached, proceeding to Phase 2");
                return;
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
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

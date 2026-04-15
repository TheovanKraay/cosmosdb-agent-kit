package com.ecommerce.orderapi.config;

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
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cosmos DB configuration with lazy initialization and background warmup.
 *
 * Key patterns applied (from skills):
 * - NOT named "CosmosConfig" (avoids SDK class name collision)
 * - No @Bean for CosmosClient (avoids eager connection at startup)
 * - Lazy init with synchronized double-checked locking
 * - Background warmup thread to initialize before first request
 * - Gateway mode for emulator compatibility
 * - contentResponseOnWriteEnabled(true) so writes return the created document
 * - /customerId partition key (high cardinality, aligns with customer queries)
 * - Composite indexes for status+createdAt queries
 */
@Component
public class CosmosDbConfiguration {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Value("${cosmos.container}")
    private String containerName;

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer cosmosContainer;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    /**
     * Background warmup thread that polls the emulator endpoint, then
     * initializes the CosmosClient, database, and container.
     * Uses a daemon thread with infinite retry (the CI health-check timeout
     * at 120s is the real constraint).
     */
    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            System.out.println("[Warmup] Starting Cosmos DB warmup...");

            // Phase 1: Poll the endpoint until it responds (any non-503 HTTP status).
            // The emulator returns 401 for unauthenticated GET — that means it IS running.
            // Only 503 and connection failures mean "still starting up".
            // Fallback: proceed after 60s regardless.
            long phase1Deadline = System.currentTimeMillis() + 60_000;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    URL url = new URL(endpoint);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    System.out.println("[Warmup] Phase 1: endpoint returned HTTP " + code);
                    if (code != 503) {
                        System.out.println("[Warmup] Emulator endpoint is reachable (HTTP " + code + ").");
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("[Warmup] Phase 1: connection failed (" + e.getMessage() + ")");
                }
                if (System.currentTimeMillis() >= phase1Deadline) {
                    System.out.println("[Warmup] Phase 1: 60s fallback timeout reached, proceeding to Phase 2.");
                    break;
                }
                try { Thread.sleep(2000); } catch (InterruptedException ie) { return; }
            }

            // Phase 2: Initialize the Cosmos client, database, and container
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    CosmosContainer container = getContainer();
                    if (container != null) {
                        System.out.println("[Warmup] Cosmos DB container initialized successfully.");
                        ready.set(true);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("[Warmup] Init attempt failed: " + e.getMessage());
                    // Reset client for fresh state on retry
                    synchronized (this) {
                        if (cosmosClient != null) {
                            try { cosmosClient.close(); } catch (Exception ignored) {}
                            cosmosClient = null;
                            cosmosContainer = null;
                        }
                    }
                }
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
            }
        });
        warmupThread.setDaemon(true);
        warmupThread.setName("cosmos-warmup");
        warmupThread.start();
    }

    /**
     * Lazy-initialized container with synchronized double-checked locking.
     * Creates database and container if they don't exist.
     */
    public CosmosContainer getContainer() {
        if (cosmosContainer != null) {
            return cosmosContainer;
        }
        synchronized (this) {
            if (cosmosContainer != null) {
                return cosmosContainer;
            }

            // Build client with gateway mode (required for emulator)
            // and contentResponseOnWriteEnabled so writes return the created document
            boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
            CosmosClientBuilder builder = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .contentResponseOnWriteEnabled(true)
                    .consistencyLevel(ConsistencyLevel.SESSION);

            if (isEmulator) {
                builder.gatewayMode();
            } else {
                builder.directMode();
            }

            cosmosClient = builder.buildClient();

            // Create database if not exists
            cosmosClient.createDatabaseIfNotExists(databaseName);
            CosmosDatabase database = cosmosClient.getDatabase(databaseName);

            // Define container with /customerId partition key
            CosmosContainerProperties containerProps = new CosmosContainerProperties(
                    containerName, "/customerId");

            // Custom indexing policy: exclude unused paths, add composite indexes
            IndexingPolicy indexingPolicy = new IndexingPolicy();
            indexingPolicy.setAutomatic(true);

            // Include paths we query on
            List<IncludedPath> includedPaths = new ArrayList<>();
            includedPaths.add(new IncludedPath("/customerId/?"));
            includedPaths.add(new IncludedPath("/status/?"));
            includedPaths.add(new IncludedPath("/createdAt/?"));
            includedPaths.add(new IncludedPath("/orderId/?"));
            indexingPolicy.setIncludedPaths(includedPaths);

            // Exclude everything else to save write RUs
            List<ExcludedPath> excludedPaths = new ArrayList<>();
            excludedPaths.add(new ExcludedPath("/*"));
            indexingPolicy.setExcludedPaths(excludedPaths);

            // Composite index for status + createdAt queries (admin queries)
            List<CompositePath> compositePathList = new ArrayList<>();
            CompositePath statusPath = new CompositePath();
            statusPath.setPath("/status");
            statusPath.setOrder(CompositePathSortOrder.ASCENDING);
            compositePathList.add(statusPath);

            CompositePath createdAtPath = new CompositePath();
            createdAtPath.setPath("/createdAt");
            createdAtPath.setOrder(CompositePathSortOrder.DESCENDING);
            compositePathList.add(createdAtPath);

            indexingPolicy.setCompositeIndexes(Collections.singletonList(compositePathList));

            containerProps.setIndexingPolicy(indexingPolicy);

            // Create container with 400 RU/s (minimum for development)
            database.createContainerIfNotExists(containerProps,
                    ThroughputProperties.createManualThroughput(400));

            cosmosContainer = database.getContainer(containerName);
            return cosmosContainer;
        }
    }
}

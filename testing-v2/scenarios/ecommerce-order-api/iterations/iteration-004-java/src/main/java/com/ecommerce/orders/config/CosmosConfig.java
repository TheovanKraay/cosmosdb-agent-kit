package com.ecommerce.orders.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.ThrottlingRetryOptions;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cosmos DB configuration with lazy initialization and two-phase warmup.
 * <p>
 * Phase 1: HTTP poll the endpoint until it returns 200 (avoids wasting SDK timeout budget).
 * Phase 2: Build CosmosClient + create database/container after endpoint is ready.
 * <p>
 * Health endpoint gates on isReady() flag, returning 503 until container is initialized.
 * Uses gateway mode for localhost/emulator, direct mode for production.
 */
@Component
public class CosmosConfig {

    private static final Logger log = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Value("${cosmos.container}")
    private String containerName;

    private volatile CosmosClient client;
    private volatile CosmosContainer container;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    public CosmosContainer getContainer() {
        return container;
    }

    @PostConstruct
    public void init() {
        Thread warmupThread = new Thread(this::warmup, "cosmos-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    /**
     * Two-phase warmup:
     * Phase 1 - Poll endpoint with lightweight HTTP GET until 200.
     * Phase 2 - Build client and create database/container.
     */
    private void warmup() {
        log.info("Starting Cosmos DB warmup (endpoint: {})", endpoint);

        // Phase 1: Poll the endpoint until it responds with HTTP 200
        while (!Thread.currentThread().isInterrupted()) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    log.info("Cosmos DB endpoint is reachable (HTTP {})", code);
                    break;
                }
                log.debug("Endpoint returned HTTP {}, retrying...", code);
            } catch (Exception e) {
                log.debug("Endpoint not reachable: {}, retrying...", e.getMessage());
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Phase 2: Build client and initialize database/container with retry
        while (!Thread.currentThread().isInterrupted()) {
            try {
                initializeCosmosResources();
                ready.set(true);
                log.info("Cosmos DB warmup complete — container '{}' ready", containerName);
                return;
            } catch (Exception e) {
                log.warn("Cosmos init attempt failed: {}", e.getMessage());
                // Close and null the client for a fresh retry
                if (client != null) {
                    try { client.close(); } catch (Exception ignored) {}
                    client = null;
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

    private void initializeCosmosResources() {
        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        ThrottlingRetryOptions retryOptions = new ThrottlingRetryOptions()
                .setMaxRetryAttemptsOnThrottledRequests(9)
                .setMaxRetryWaitTime(Duration.ofSeconds(30));

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .throttlingRetryOptions(retryOptions);

        if (isEmulator) {
            builder.gatewayMode();
        } else {
            builder.directMode();
        }

        client = builder.buildClient();

        // Create database if not exists
        client.createDatabaseIfNotExists(databaseName);
        CosmosDatabase database = client.getDatabase(databaseName);

        // Configure container with custom indexing policy
        CosmosContainerProperties containerProps = new CosmosContainerProperties(containerName, "/customerId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setAutomatic(true);
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);

        // Include key query paths
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/customerId/?"),
                new IncludedPath("/status/?"),
                new IncludedPath("/createdAt/?"),
                new IncludedPath("/type/?"),
                new IncludedPath("/orderId/?")
        ));

        // Exclude unused paths to reduce write RU cost
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/shippingAddress/?"),
                new ExcludedPath("/items/*"),
                new ExcludedPath("/\"_etag\"/?")
        ));

        // Composite indexes for efficient multi-field ORDER BY queries
        CompositePath statusAsc = new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtDesc = new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.DESCENDING);
        CompositePath customerAsc = new CompositePath().setPath("/customerId").setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath createdAtAsc = new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.ASCENDING);

        indexingPolicy.setCompositeIndexes(Arrays.asList(
                Arrays.asList(statusAsc, createdAtDesc),
                Arrays.asList(customerAsc, createdAtDesc),
                Arrays.asList(statusAsc, createdAtAsc)
        ));

        containerProps.setIndexingPolicy(indexingPolicy);

        // Create container with autoscale throughput
        database.createContainerIfNotExists(containerProps,
                ThroughputProperties.createAutoscaledThroughput(1000));

        container = database.getContainer(containerName);

        log.info("Cosmos DB container '{}' initialized with custom indexing policy", containerName);
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            try {
                client.close();
                log.info("CosmosClient closed");
            } catch (Exception e) {
                log.warn("Error closing CosmosClient: {}", e.getMessage());
            }
        }
    }
}

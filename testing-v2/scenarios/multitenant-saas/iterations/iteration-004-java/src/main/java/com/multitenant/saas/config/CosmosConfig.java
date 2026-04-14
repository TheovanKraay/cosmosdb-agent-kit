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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

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
    private static final long EMULATOR_POLL_INTERVAL_MS = 2000;

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private volatile boolean ready = false;

    /**
     * Lightweight HTTP check to verify the Cosmos DB endpoint is responding.
     * Uses a short timeout (2s) to avoid wasting time when the emulator is still starting.
     * Returns true only when the endpoint responds with HTTP 200.
     */
    private boolean isEndpointReady() {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection && isEmulatorEndpoint()) {
                // Only disable hostname verification for the local emulator's self-signed cert
                ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) ->
                        "localhost".equals(hostname) || "127.0.0.1".equals(hostname));
            }
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            try {
                int code = conn.getResponseCode();
                return code == 200;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isEmulatorEndpoint() {
        return endpoint != null
                && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"));
    }

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            logger.info("Starting Cosmos DB warmup in background (daemon thread)...");
            // Phase 1: Poll the emulator endpoint until it responds with 200.
            // This avoids wasting time in the SDK's internal ~23s timeout per call
            // when the emulator is still returning 503/10001 (service starting).
            while (!Thread.currentThread().isInterrupted()) {
                if (isEndpointReady()) {
                    logger.info("Cosmos DB endpoint is responding, proceeding to initialize...");
                    break;
                }
                try {
                    Thread.sleep(EMULATOR_POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            // Phase 2: Endpoint is ready — build client and create database/container.
            // Retry in a loop in case the first SDK call still fails transiently.
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    getContainer();
                    ready = true;
                    logger.info("Cosmos DB warmup completed successfully");
                    return;
                } catch (Exception e) {
                    logger.warn("Cosmos DB init failed, retrying in 2s: {}", e.getMessage());
                    // Close stale client so next attempt builds a fresh one
                    closeClient();
                    try {
                        Thread.sleep(EMULATOR_POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
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

        if (cosmosClient == null) {
            CosmosClientBuilder builder = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .consistencyLevel(ConsistencyLevel.SESSION)
                    .contentResponseOnWriteEnabled(true);

            if (isEmulatorEndpoint()) {
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
        logger.info("Cosmos DB container initialized successfully");
        return container;
    }

    private synchronized void closeClient() {
        if (cosmosClient != null) {
            try {
                cosmosClient.close();
            } catch (Exception e) {
                logger.debug("Error closing stale CosmosClient: {}", e.getMessage());
            }
            cosmosClient = null;
        }
    }

    @PreDestroy
    public void cleanup() {
        closeClient();
        logger.info("Cosmos DB client closed");
    }
}

package com.iot.telemetry.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    private CosmosAsyncClient cosmosClient;
    private CosmosAsyncDatabase database;
    private CosmosAsyncContainer devicesContainer;
    private CosmosAsyncContainer telemetryContainer;

    private final AtomicBoolean isReady = new AtomicBoolean(false);

    // Rule 4.22: Reuse CosmosClient as Singleton
    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        // Rule 4.8: Gateway mode for emulator; Rule 4.11: enable content response on write
        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .gatewayMode()
                .buildAsyncClient();
        return this.cosmosClient;
    }

    // Rule 4.12: Use dependent @Bean methods for initialization
    @Bean
    public CosmosAsyncDatabase cosmosDatabase(CosmosAsyncClient client) {
        this.database = client.getDatabase(databaseName);
        return this.database;
    }

    @Bean(name = "devicesContainer")
    public CosmosAsyncContainer devicesContainer(CosmosAsyncDatabase db) {
        this.devicesContainer = db.getContainer("devices");
        return this.devicesContainer;
    }

    @Bean(name = "telemetryContainer")
    public CosmosAsyncContainer telemetryContainer(CosmosAsyncDatabase db) {
        this.telemetryContainer = db.getContainer("telemetry");
        return this.telemetryContainer;
    }

    @PostConstruct
    public void initialize() {
        Thread initThread = new Thread(() -> {
            try {
                // Phase 1: Poll endpoint until emulator is reachable
                waitForEmulator();
                // Phase 2: Initialize Cosmos DB resources
                initializeCosmosResources();
                isReady.set(true);
                logger.info("Cosmos DB initialization complete - application is ready");
            } catch (Exception e) {
                logger.error("Failed to initialize Cosmos DB", e);
            }
        });
        initThread.setDaemon(true);
        initThread.start();
    }

    private void waitForEmulator() {
        logger.info("Phase 1: Waiting for Cosmos DB emulator at {}", endpoint);
        long startTime = System.currentTimeMillis();
        long maxWaitMs = 60_000; // 60 second fallback timeout

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                // Accept ANY non-503 response as evidence emulator is running
                int status = conn.getResponseCode();
                conn.disconnect();
                if (status != 503) {
                    logger.info("Phase 1 complete: Emulator responded with HTTP {}", status);
                    return;
                }
                logger.info("Emulator returned 503, retrying...");
            } catch (Exception e) {
                logger.info("Emulator not reachable yet: {}", e.getMessage());
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.info("Phase 1: Fallback timeout reached (60s), proceeding to Phase 2");
    }

    private void initializeCosmosResources() {
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Phase 2: Initializing Cosmos DB resources (attempt {}/{})", attempt, maxRetries);

                // Create database if not exists
                cosmosAsyncClient().createDatabaseIfNotExists(databaseName,
                        ThroughputProperties.createManualThroughput(400)).block(Duration.ofSeconds(30));

                // Create devices container - partitioned by deviceId
                CosmosContainerProperties devicesProps = new CosmosContainerProperties("devices", "/deviceId");
                IndexingPolicy devicesIndexingPolicy = new IndexingPolicy();
                devicesIndexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
                devicesIndexingPolicy.setIncludedPaths(Arrays.asList(
                        new IncludedPath("/*")
                ));
                // Rule 5.1/5.2: Composite index for location queries
                CompositePath locationPath = new CompositePath();
                locationPath.setPath("/location");
                locationPath.setOrder(CompositePathSortOrder.ASCENDING);
                CompositePath deviceIdPath = new CompositePath();
                deviceIdPath.setPath("/deviceId");
                deviceIdPath.setOrder(CompositePathSortOrder.ASCENDING);
                devicesIndexingPolicy.setCompositeIndexes(
                        Collections.singletonList(Arrays.asList(locationPath, deviceIdPath))
                );
                devicesProps.setIndexingPolicy(devicesIndexingPolicy);
                cosmosAsyncClient().getDatabase(databaseName)
                        .createContainerIfNotExists(devicesProps).block(Duration.ofSeconds(30));

                // Create telemetry container - partitioned by deviceId, with TTL
                CosmosContainerProperties telemetryProps = new CosmosContainerProperties("telemetry", "/deviceId");
                // TTL: 30 days = 2592000 seconds
                telemetryProps.setDefaultTimeToLiveInSeconds(2592000);

                // Rule 5.3: Selective indexing for time-series data
                IndexingPolicy telemetryIndexingPolicy = new IndexingPolicy();
                telemetryIndexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
                telemetryIndexingPolicy.setIncludedPaths(Arrays.asList(
                        new IncludedPath("/deviceId/?"),
                        new IncludedPath("/timestamp/?"),
                        new IncludedPath("/temperature/?"),
                        new IncludedPath("/humidity/?"),
                        new IncludedPath("/batteryLevel/?")
                ));
                telemetryIndexingPolicy.setExcludedPaths(Arrays.asList(
                        new ExcludedPath("/*"),
                        new ExcludedPath("/\"_etag\"/?")
                ));

                // Rule 5.1: Composite index for ORDER BY timestamp DESC queries
                CompositePath telDeviceIdPath = new CompositePath();
                telDeviceIdPath.setPath("/deviceId");
                telDeviceIdPath.setOrder(CompositePathSortOrder.ASCENDING);
                CompositePath timestampPath = new CompositePath();
                timestampPath.setPath("/timestamp");
                timestampPath.setOrder(CompositePathSortOrder.DESCENDING);
                telemetryIndexingPolicy.setCompositeIndexes(
                        Collections.singletonList(Arrays.asList(telDeviceIdPath, timestampPath))
                );
                telemetryProps.setIndexingPolicy(telemetryIndexingPolicy);

                cosmosAsyncClient().getDatabase(databaseName)
                        .createContainerIfNotExists(telemetryProps).block(Duration.ofSeconds(30));

                logger.info("Phase 2 complete: Database and containers created successfully");
                return;
            } catch (Exception e) {
                logger.warn("Phase 2 attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    logger.error("All Phase 2 attempts exhausted", e);
                    throw new RuntimeException("Failed to initialize Cosmos DB after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public boolean isReady() {
        return isReady.get();
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
            logger.info("CosmosClient closed");
        }
    }
}

package com.iot.telemetry.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.ThrottlingRetryOptions;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKeyDefinitionVersion;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    private static final String DEVICES_CONTAINER = "devices";
    private static final String TELEMETRY_CONTAINER = "telemetry";
    private static final int TELEMETRY_TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days

    private CosmosAsyncClient cosmosClient;
    private CosmosAsyncDatabase database;
    private CosmosAsyncContainer devicesContainer;
    private CosmosAsyncContainer telemetryContainer;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        Thread initThread = new Thread(() -> {
            try {
                // Phase 1: Poll endpoint until reachable
                logger.info("Phase 1: Polling Cosmos DB endpoint: {}", endpoint);
                pollEndpoint();

                // Phase 2: Initialize SDK client and containers
                logger.info("Phase 2: Initializing Cosmos DB client and containers");
                initializeCosmosDb();
                ready.set(true);
                logger.info("Cosmos DB initialization complete");
            } catch (Exception e) {
                logger.error("Failed to initialize Cosmos DB", e);
            }
        }, "cosmos-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    private void pollEndpoint() {
        long startTime = System.currentTimeMillis();
        long fallbackTimeout = 60_000; // 60 seconds max

        while (System.currentTimeMillis() - startTime < fallbackTimeout) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");

                // Accept ANY response from the server that isn't HTTPS redirect or SSL error
                javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                }}, new java.security.SecureRandom());
                if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                    ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
                    ((javax.net.ssl.HttpsURLConnection) conn).setHostnameVerifier((h, s) -> true);
                }

                int status = conn.getResponseCode();
                conn.disconnect();

                // Accept ANY non-503 response as evidence emulator is reachable
                if (status != 503) {
                    logger.info("Phase 1: Endpoint reachable (HTTP {})", status);
                    return;
                }
                logger.info("Phase 1: Got 503, retrying...");
            } catch (Exception e) {
                logger.info("Phase 1: Connection failed ({}), retrying...", e.getMessage());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.warn("Phase 1: Fallback timeout reached, proceeding to Phase 2 anyway");
    }

    private void initializeCosmosDb() {
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (cosmosClient != null) {
                    try { cosmosClient.close(); } catch (Exception ignored) {}
                    cosmosClient = null;
                }

                ThrottlingRetryOptions retryOptions = new ThrottlingRetryOptions();
                retryOptions.setMaxRetryAttemptsOnThrottledRequests(9);
                retryOptions.setMaxRetryWaitTime(Duration.ofSeconds(30));

                cosmosClient = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .key(key)
                        .consistencyLevel(ConsistencyLevel.SESSION)
                        .gatewayMode(GatewayConnectionConfig.getDefaultConfig())
                        .throttlingRetryOptions(retryOptions)
                        .contentResponseOnWriteEnabled(true)
                        .buildAsyncClient();

                // Create database
                cosmosClient.createDatabaseIfNotExists(databaseName,
                        ThroughputProperties.createManualThroughput(400)).block();
                database = cosmosClient.getDatabase(databaseName);

                // Create devices container with deviceId as partition key
                createDevicesContainer();

                // Create telemetry container with deviceId as partition key + TTL
                createTelemetryContainer();

                logger.info("Phase 2: Containers initialized successfully (attempt {})", attempt);
                return;
            } catch (Exception e) {
                logger.warn("Phase 2: Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to initialize Cosmos DB after " + maxRetries + " attempts", e);
                }
                try { Thread.sleep(5000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void createDevicesContainer() {
        CosmosContainerProperties properties = new CosmosContainerProperties(DEVICES_CONTAINER, "/deviceId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/deviceId/?"),
                new IncludedPath("/location/?"),
                new IncludedPath("/deviceType/?")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/\"_etag\"/?"),
                new ExcludedPath("/*")
        ));
        properties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(properties).block();
        devicesContainer = database.getContainer(DEVICES_CONTAINER);
    }

    private void createTelemetryContainer() {
        // Use hierarchical partition key: deviceId
        CosmosContainerProperties properties = new CosmosContainerProperties(TELEMETRY_CONTAINER, "/deviceId");

        // Enable TTL for 30-day auto-expiration
        properties.setDefaultTimeToLiveInSeconds(TELEMETRY_TTL_SECONDS);

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/deviceId/?"),
                new IncludedPath("/timestamp/?")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/\"_etag\"/?"),
                new ExcludedPath("/*")
        ));

        // Composite index for ORDER BY deviceId, timestamp DESC (time-range queries)
        List<CompositePath> compositePaths = new ArrayList<>();
        CompositePath pathDeviceId = new CompositePath();
        pathDeviceId.setPath("/deviceId");
        pathDeviceId.setOrder(CompositePathSortOrder.ASCENDING);
        compositePaths.add(pathDeviceId);
        CompositePath pathTimestamp = new CompositePath();
        pathTimestamp.setPath("/timestamp");
        pathTimestamp.setOrder(CompositePathSortOrder.DESCENDING);
        compositePaths.add(pathTimestamp);
        indexingPolicy.setCompositeIndexes(Collections.singletonList(compositePaths));

        properties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(properties).block();
        telemetryContainer = database.getContainer(TELEMETRY_CONTAINER);
    }

    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        return cosmosClient;
    }

    public CosmosAsyncDatabase getDatabase() {
        return database;
    }

    public CosmosAsyncContainer getDevicesContainer() {
        return devicesContainer;
    }

    public CosmosAsyncContainer getTelemetryContainer() {
        return telemetryContainer;
    }

    public boolean isReady() {
        return ready.get();
    }
}

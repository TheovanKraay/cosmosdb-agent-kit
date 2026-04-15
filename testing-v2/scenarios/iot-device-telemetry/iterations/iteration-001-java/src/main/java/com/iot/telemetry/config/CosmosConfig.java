package com.iot.telemetry.config;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cosmos DB configuration with two-phase warmup for emulator compatibility.
 *
 * Best Practices Applied:
 * - Rule 4.1/4.2: Singleton CosmosAsyncClient via Spring @Bean
 * - Rule 4.3: Throttling retry for 429 handling (9 retries, 30s max wait)
 * - Rule 4.4: Gateway mode for emulator, Direct mode for production
 * - Rule 4.9: contentResponseOnWriteEnabled for write responses
 * - Rule 5.1: Composite indexes for ORDER BY queries
 * - Rule 5.2: Selective indexing policy
 */
@Configuration
public class CosmosConfig {

    private static final Logger log = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    private CosmosAsyncClient client;
    private volatile boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        // Phase 1: Poll endpoint until emulator is reachable
        waitForEndpoint();

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
            log.info("Configuring for Cosmos DB Emulator (Gateway mode)");
            builder.gatewayMode();
        } else {
            log.info("Configuring for production (Direct mode)");
            builder.directMode();
        }

        this.client = builder.buildAsyncClient();
        return this.client;
    }

    @Bean
    public CosmosAsyncDatabase cosmosDatabase(CosmosAsyncClient client) {
        // Phase 2: Initialize database and containers
        log.info("Initializing database: {}", databaseName);

        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                client.createDatabaseIfNotExists(databaseName).block();
                CosmosAsyncDatabase database = client.getDatabase(databaseName);
                initializeContainers(database);
                ready = true;
                log.info("Cosmos DB initialization complete");
                return database;
            } catch (Exception e) {
                log.warn("Cosmos DB init attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to initialize Cosmos DB after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during Cosmos DB initialization", ie);
                }
            }
        }
        throw new RuntimeException("Unreachable");
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            log.info("Closing CosmosAsyncClient");
            client.close();
        }
    }

    /**
     * Phase 1: Poll the Cosmos endpoint until it responds.
     * Accept ANY non-503 HTTP response (including 401) as evidence the emulator is reachable.
     * 60-second fallback timeout.
     */
    private void waitForEndpoint() {
        log.info("Phase 1: Waiting for Cosmos endpoint: {}", endpoint);
        long deadline = System.currentTimeMillis() + 60_000;

        while (System.currentTimeMillis() < deadline) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");

                // Trust all certs for emulator
                if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                    javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;
                    javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                    sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }}, new java.security.SecureRandom());
                    httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                    httpsConn.setHostnameVerifier((h, s) -> true);
                }

                int status = conn.getResponseCode();
                conn.disconnect();

                if (status != 503) {
                    log.info("Phase 1: Endpoint reachable (HTTP {})", status);
                    return;
                }
                log.info("Phase 1: Endpoint returned 503, retrying...");
            } catch (Exception e) {
                log.info("Phase 1: Endpoint not ready ({}), retrying...", e.getMessage());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        log.warn("Phase 1: 60s timeout reached, proceeding to Phase 2 anyway");
    }

    private void initializeContainers(CosmosAsyncDatabase database) {
        createDevicesContainer(database);
        createTelemetryContainer(database);
    }

    /**
     * Create devices container with partition key /deviceId.
     */
    private void createDevicesContainer(CosmosAsyncDatabase database) {
        CosmosContainerProperties props = new CosmosContainerProperties("devices", "/deviceId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setAutomatic(true);
        indexingPolicy.setIncludedPaths(Arrays.asList(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(Arrays.asList(new ExcludedPath("/_etag/?")));
        props.setIndexingPolicy(indexingPolicy);

        ThroughputProperties throughput = ThroughputProperties.createManualThroughput(400);

        database.createContainerIfNotExists(props, throughput).block();
        log.info("Devices container initialized");
    }

    /**
     * Create telemetry container with partition key /deviceId and TTL enabled.
     *
     * Best Practices:
     * - Rule 5.1: Composite index for ORDER BY timestamp DESC queries
     * - TTL enabled at container level, set per-document to 30 days
     */
    private void createTelemetryContainer(CosmosAsyncDatabase database) {
        CosmosContainerProperties props = new CosmosContainerProperties("telemetry", "/deviceId");

        // Enable TTL at container level: 30 days = 2592000 seconds
        props.setDefaultTimeToLiveInSeconds(2592000);

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setAutomatic(true);
        indexingPolicy.setIncludedPaths(Arrays.asList(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(Arrays.asList(new ExcludedPath("/_etag/?")));

        // Composite index for ORDER BY timestamp DESC queries
        List<List<CompositePath>> compositeIndexes = new ArrayList<>();

        List<CompositePath> deviceTimestamp = new ArrayList<>();
        deviceTimestamp.add(new CompositePath().setPath("/deviceId").setOrder(CompositePathSortOrder.ASCENDING));
        deviceTimestamp.add(new CompositePath().setPath("/timestamp").setOrder(CompositePathSortOrder.DESCENDING));
        compositeIndexes.add(deviceTimestamp);

        indexingPolicy.setCompositeIndexes(compositeIndexes);
        props.setIndexingPolicy(indexingPolicy);

        ThroughputProperties throughput = ThroughputProperties.createManualThroughput(400);

        database.createContainerIfNotExists(props, throughput).block();
        log.info("Telemetry container initialized");
    }
}

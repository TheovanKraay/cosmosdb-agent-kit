package com.iot.telemetry.config;

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
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cosmos DB configuration with two-phase emulator startup.
 *
 * Best practices applied:
 * - Rule 4.16/4.22: Singleton CosmosClient (registered as Spring bean)
 * - Rule 4.6:  Gateway mode for emulator
 * - Rule 4.9/4.11:  contentResponseOnWriteEnabled for Java SDK
 * - Rule 4.13: Spring Boot 3.x requires Java 17+
 * - Rule 5.1/5.2: Composite indexes for ORDER BY queries
 * - Rule 5.3: Selective indexing for write-heavy telemetry
 */
@Configuration
public class CosmosDbConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDbConfig.class);

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    private CosmosClient cosmosClient;
    private final AtomicBoolean isReady = new AtomicBoolean(false);

    public boolean isReady() {
        return isReady.get();
    }

    /**
     * Phase 1: Poll emulator endpoint with lightweight HTTP GET.
     * Accept any non-503 response (including 401) as emulator ready.
     * Fallback: proceed after 60 seconds regardless.
     */
    private void waitForEmulatorReady() {
        logger.info("Phase 1: Polling Cosmos DB endpoint for readiness: {}", endpoint);
        long startTime = System.currentTimeMillis();
        long timeout = 60_000; // 60s fallback

        while (System.currentTimeMillis() - startTime < timeout) {
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
                    sc.init(null, new javax.net.ssl.TrustManager[]{
                        new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String t) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String t) {}
                        }
                    }, new java.security.SecureRandom());
                    httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                    httpsConn.setHostnameVerifier((hostname, session) -> true);
                }

                int status = conn.getResponseCode();
                logger.info("Phase 1: Got HTTP {} from emulator", status);
                if (status != 503) {
                    logger.info("Phase 1: Emulator is reachable (HTTP {})", status);
                    return;
                }
            } catch (Exception e) {
                logger.info("Phase 1: Waiting for emulator... ({})", e.getMessage());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.info("Phase 1: Timeout reached (60s). Proceeding to Phase 2 anyway.");
    }

    @Bean
    public CosmosClient cosmosClient() {
        waitForEmulatorReady();

        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true);

        if (isEmulator) {
            builder.gatewayMode();
            logger.info("Using Gateway mode for Cosmos DB Emulator");
        } else {
            builder.directMode();
            logger.info("Using Direct mode for production Cosmos DB");
        }

        logger.info("Phase 2: Building CosmosClient...");
        this.cosmosClient = builder.buildClient();
        return this.cosmosClient;
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        logger.info("Creating database '{}' if not exists...", databaseName);
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    /**
     * Devices container: partition key = /deviceId
     * Stores device metadata. No TTL needed.
     */
    @Bean
    public CosmosContainer devicesContainer(CosmosDatabase database) {
        String containerName = "devices";
        CosmosContainerProperties props = new CosmosContainerProperties(containerName, "/deviceId");

        // Indexing policy for devices — default is fine for small metadata
        database.createContainerIfNotExists(props,
                ThroughputProperties.createManualThroughput(400));

        logger.info("Initialized 'devices' container with partition key /deviceId");
        return database.getContainer(containerName);
    }

    /**
     * Telemetry container: partition key = /deviceId
     * Stores time-series readings with 30-day TTL.
     *
     * Rule 2.1/2.4/2.7: deviceId is high-cardinality, immutable, and aligns with query patterns.
     * Rule 5.1/5.2: Composite index on (deviceId ASC, timestamp DESC) for time-range queries.
     * Rule 5.3: Exclude unused paths for write-heavy workload.
     */
    @Bean
    public CosmosContainer telemetryContainer(CosmosDatabase database) {
        String containerName = "telemetry";
        CosmosContainerProperties props = new CosmosContainerProperties(containerName, "/deviceId");

        // TTL: 30 days in seconds (2,592,000)
        props.setDefaultTimeToLiveInSeconds(2592000);

        // Indexing policy: selective for write optimization
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setAutomatic(true);

        // Include only the paths we query on
        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/deviceId/?"));
        includedPaths.add(new IncludedPath("/timestamp/?"));
        includedPaths.add(new IncludedPath("/temperature/?"));
        includedPaths.add(new IncludedPath("/humidity/?"));
        includedPaths.add(new IncludedPath("/batteryLevel/?"));
        indexingPolicy.setIncludedPaths(includedPaths);

        // Exclude root wildcard (mandatory when using selective included paths)
        List<ExcludedPath> excludedPaths = new ArrayList<>();
        excludedPaths.add(new ExcludedPath("/*"));
        indexingPolicy.setExcludedPaths(excludedPaths);

        // Composite index for ORDER BY deviceId + timestamp queries
        List<CompositePath> composite1 = new ArrayList<>();
        composite1.add(new CompositePath().setPath("/deviceId").setOrder(CompositePathSortOrder.ASCENDING));
        composite1.add(new CompositePath().setPath("/timestamp").setOrder(CompositePathSortOrder.DESCENDING));

        List<CompositePath> composite2 = new ArrayList<>();
        composite2.add(new CompositePath().setPath("/deviceId").setOrder(CompositePathSortOrder.ASCENDING));
        composite2.add(new CompositePath().setPath("/timestamp").setOrder(CompositePathSortOrder.ASCENDING));

        indexingPolicy.setCompositeIndexes(Arrays.asList(composite1, composite2));
        props.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(props,
                ThroughputProperties.createManualThroughput(400));

        logger.info("Initialized 'telemetry' container with partition key /deviceId and 30-day TTL");

        // Mark as ready after all containers are initialized
        isReady.set(true);
        logger.info("Cosmos DB initialization complete. Application is ready.");

        return database.getContainer(containerName);
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
            logger.info("Cosmos DB client closed");
        }
    }
}

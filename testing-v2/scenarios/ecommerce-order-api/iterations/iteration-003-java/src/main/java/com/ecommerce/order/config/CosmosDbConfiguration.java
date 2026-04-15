package com.ecommerce.order.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;

/**
 * Cosmos DB configuration with lazy initialization and background warmup.
 * Uses two-phase startup:
 * Phase 1: Poll emulator endpoint with lightweight HTTP GET until ready
 * Phase 2: Build CosmosClient and initialize database/container
 *
 * NOT a @Bean — fully lazy to avoid SSL failures at startup.
 * Uses gateway mode for emulator compatibility.
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
    private volatile boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    @PostConstruct
    public void startWarmup() {
        Thread warmupThread = new Thread(this::warmup);
        warmupThread.setDaemon(true);
        warmupThread.setName("cosmos-warmup");
        warmupThread.start();
    }

    private void warmup() {
        logger.info("Starting Cosmos DB warmup...");

        // Phase 1: Poll endpoint until it responds with HTTP 200
        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
        if (isEmulator) {
            logger.info("Detected emulator endpoint, polling until ready...");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    URL url = new URL(endpoint);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.setRequestMethod("GET");
                    // Reuse TrustAllProvider's trust managers for emulator self-signed cert
                    if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                        javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) conn;
                        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance("PKIX");
                        tmf.init((KeyStore) null);
                        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                        sc.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
                        httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                        httpsConn.setHostnameVerifier((hostname, session) ->
                                "localhost".equals(hostname) || "127.0.0.1".equals(hostname));
                    }
                    int status = conn.getResponseCode();
                    conn.disconnect();
                    if (status == 200) {
                        logger.info("Emulator endpoint is ready (HTTP 200)");
                        break;
                    }
                    logger.info("Emulator returned HTTP {}, retrying in 2s...", status);
                } catch (Exception e) {
                    logger.info("Emulator not ready ({}), retrying in 2s...", e.getMessage());
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Phase 2: Initialize CosmosClient with retry
        while (!Thread.currentThread().isInterrupted()) {
            try {
                initializeClient();
                logger.info("Cosmos DB warmup complete - ready to serve requests");
                return;
            } catch (Exception e) {
                logger.warn("Cosmos DB init failed ({}), retrying in 1s...", e.getMessage());
                // Close and null for fresh state on retry
                if (cosmosClient != null) {
                    try {
                        cosmosClient.close();
                    } catch (Exception ignored) {
                    }
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

    private synchronized void initializeClient() {
        if (ready) return;

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true);

        // Use Gateway mode for emulator, Direct mode for production
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
        database.createContainerIfNotExists(containerProps,
                ThroughputProperties.createManualThroughput(400));

        container = database.getContainer(containerName);
        ready = true;
    }

    public synchronized CosmosContainer getContainer() {
        if (!ready) {
            initializeClient();
        }
        return container;
    }
}

package com.ecommerce.order.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cosmos DB configuration with two-phase lazy initialization and warmup.
 *
 * Phase 1: Poll the emulator endpoint with lightweight HTTP GET until it responds with 200.
 * Phase 2: Build CosmosClient and create database/container.
 *
 * The health endpoint gates on isReady() — returns 503 until initialization completes.
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

    public boolean isReady() {
        return ready.get();
    }

    public CosmosContainer getContainer() {
        return container;
    }

    public CosmosClient getCosmosClient() {
        return cosmosClient;
    }

    @PostConstruct
    public void startWarmup() {
        Thread warmupThread = new Thread(this::warmup);
        warmupThread.setDaemon(true);
        warmupThread.setName("cosmos-warmup");
        warmupThread.start();
    }

    private void warmup() {
        // Phase 1: Poll emulator endpoint until it's reachable
        logger.info("Cosmos warmup Phase 1: polling endpoint {} ...", endpoint);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                // Accept self-signed cert for localhost
                if (conn instanceof javax.net.ssl.HttpsURLConnection httpsConn) {
                    httpsConn.setHostnameVerifier((hostname, session) ->
                        hostname.equals("localhost") || hostname.equals("127.0.0.1"));
                    javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                    sc.init(null, new javax.net.ssl.TrustManager[]{
                        new javax.net.ssl.X509TrustManager() {
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                        }
                    }, null);
                    httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                }
                int status = conn.getResponseCode();
                conn.disconnect();
                if (status == 200) {
                    logger.info("Cosmos emulator endpoint is ready (HTTP {})", status);
                    break;
                }
            } catch (Exception e) {
                logger.debug("Cosmos endpoint not ready yet: {}", e.getMessage());
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Phase 2: Build client and create database/container with retry
        logger.info("Cosmos warmup Phase 2: initializing client and containers...");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                initializeCosmosResources();
                ready.set(true);
                logger.info("Cosmos DB initialization complete. Database={}, Container={}",
                    databaseName, containerName);
                return;
            } catch (Exception e) {
                logger.warn("Cosmos init failed, retrying in 2s: {}", e.getMessage());
                // Close and null the client for a fresh state on retry
                if (cosmosClient != null) {
                    try { cosmosClient.close(); } catch (Exception ignored) {}
                    cosmosClient = null;
                    container = null;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void initializeCosmosResources() {
        CosmosClientBuilder builder = new CosmosClientBuilder()
            .endpoint(endpoint)
            .key(key)
            .consistencyLevel(ConsistencyLevel.SESSION)
            .contentResponseOnWriteEnabled(true);

        // Use gateway mode for emulator (localhost), direct mode for production
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            builder.gatewayMode(GatewayConnectionConfig.getDefaultConfig());
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
    }
}

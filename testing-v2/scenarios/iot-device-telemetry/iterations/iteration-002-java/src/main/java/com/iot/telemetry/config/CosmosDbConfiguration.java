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
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class CosmosDbConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDbConfiguration.class);

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    private CosmosClient cosmosClient;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        // Phase 1: Poll endpoint until emulator is reachable
        waitForEmulator();

        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true); // Rule 4.11

        if (isEmulator) {
            builder.gatewayMode(); // Rule 4.8: Emulator requires Gateway mode
        } else {
            builder.directMode(); // Rule 4.5: Production uses Direct mode
        }

        this.cosmosClient = builder.buildClient();
        return this.cosmosClient;
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        // Phase 2: Create database and containers
        cosmosClient.createDatabaseIfNotExists(databaseName);
        CosmosDatabase database = cosmosClient.getDatabase(databaseName);

        // Devices container - partitioned by deviceId
        CosmosContainerProperties devicesProps = new CosmosContainerProperties("devices", "/deviceId");
        database.createContainerIfNotExists(devicesProps, ThroughputProperties.createManualThroughput(400));

        // Telemetry container - partitioned by deviceId for time-series queries
        CosmosContainerProperties telemetryProps = new CosmosContainerProperties("telemetry", "/deviceId");
        telemetryProps.setDefaultTimeToLiveInSeconds(30 * 24 * 60 * 60); // 30-day TTL

        // Indexing policy optimized for time-series queries
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/deviceId/?"),
                new IncludedPath("/timestamp/?"),
                new IncludedPath("/temperature/?"),
                new IncludedPath("/humidity/?"),
                new IncludedPath("/batteryLevel/?")
        ));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/*")
        ));
        telemetryProps.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(telemetryProps, ThroughputProperties.createManualThroughput(400));

        ready.set(true);
        logger.info("Cosmos DB initialized: database={}, containers=[devices, telemetry]", databaseName);
        return database;
    }

    @Bean
    public CosmosContainer devicesContainer(CosmosDatabase cosmosDatabase) {
        return cosmosDatabase.getContainer("devices");
    }

    @Bean
    public CosmosContainer telemetryContainer(CosmosDatabase cosmosDatabase) {
        return cosmosDatabase.getContainer("telemetry");
    }

    private void waitForEmulator() {
        long startTime = System.currentTimeMillis();
        long timeoutMs = 60_000; // 60 seconds fallback

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.setRequestMethod("GET");

                // Trust all certs for emulator
                if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                    javax.net.ssl.HttpsURLConnection httpsConn = (javax.net.ssl.HttpsURLConnection) connection;
                    javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                            new javax.net.ssl.X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            }
                    };
                    javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                    sc.init(null, trustAll, new java.security.SecureRandom());
                    httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                    httpsConn.setHostnameVerifier((h, s) -> true);
                }

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                // Accept ANY non-503 response as "emulator is ready"
                if (responseCode != 503) {
                    logger.info("Cosmos DB emulator reachable at {} (HTTP {})", endpoint, responseCode);
                    return;
                }
                logger.info("Cosmos DB emulator returned 503, retrying...");
            } catch (Exception e) {
                logger.info("Waiting for Cosmos DB emulator at {}: {}", endpoint, e.getMessage());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.warn("Emulator polling timed out after {}s, proceeding anyway", timeoutMs / 1000);
    }

    @PreDestroy
    public void cleanup() {
        // CosmosClient close handled by destroyMethod = "close"
    }
}

package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;

@Configuration
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

    private CosmosClient client;

    @Bean
    public CosmosClient cosmosClient() {
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            configureTrustAllSsl();
        }

        this.client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();

        initializeDatabase();
        return client;
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient client) {
        return client.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase database) {
        return database.getContainer(containerName);
    }

    private void configureTrustAllSsl() {
        try {
            Security.insertProviderAt(new TrustAllSslProvider(), 1);

            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            SSLContext.setDefault(sc);

            log.info("Trust-all SSL configured for Cosmos DB emulator");
        } catch (Exception e) {
            log.error("Failed to configure trust-all SSL", e);
        }
    }

    private void initializeDatabase() {
        try {
            client.createDatabaseIfNotExists(databaseName);
            CosmosDatabase db = client.getDatabase(databaseName);

            CosmosContainerProperties containerProperties =
                    new CosmosContainerProperties(containerName, "/customerId");

            db.createContainerIfNotExists(containerProperties,
                    ThroughputProperties.createManualThroughput(400));

            log.info("Cosmos DB initialized: {}/{}", databaseName, containerName);
        } catch (Exception e) {
            log.error("Failed to initialize Cosmos DB", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
    }
}

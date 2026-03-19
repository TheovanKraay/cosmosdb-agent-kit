package com.ecommerce.config;

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
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
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
import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    private CosmosAsyncClient client;

    static {
        try {
            Security.insertProviderAt(new TrustAllSslProvider(), 1);
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up trust-all SSL context", e);
        }
    }

    @Bean
    public CosmosAsyncClient cosmosAsyncClient() {
        this.client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildAsyncClient();
        return this.client;
    }

    @Bean
    public CosmosAsyncDatabase cosmosDatabase(CosmosAsyncClient client) {
        return client.getDatabase(databaseName);
    }

    @Bean
    public CosmosAsyncContainer cosmosContainer(CosmosAsyncDatabase database) {
        return database.getContainer("orders");
    }

    @PostConstruct
    public void initializeDatabase() {
        try {
            CosmosAsyncClient initClient = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .consistencyLevel(ConsistencyLevel.SESSION)
                    .gatewayMode()
                    .buildAsyncClient();

            initClient.createDatabaseIfNotExists(databaseName).block();
            CosmosAsyncDatabase db = initClient.getDatabase(databaseName);

            IndexingPolicy indexingPolicy = new IndexingPolicy();
            indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
            indexingPolicy.setAutomatic(true);
            indexingPolicy.setIncludedPaths(Arrays.asList(
                    new IncludedPath("/*")
            ));

            PartitionKeyDefinition pkDef = new PartitionKeyDefinition();
            pkDef.setPaths(Collections.singletonList("/customerId"));

            CosmosContainerProperties containerProps = new CosmosContainerProperties("orders", pkDef);
            containerProps.setIndexingPolicy(indexingPolicy);

            db.createContainerIfNotExists(containerProps, ThroughputProperties.createManualThroughput(400)).block();

            initClient.close();
            logger.info("Cosmos DB initialized: database={}, container=orders", databaseName);
        } catch (Exception e) {
            logger.error("Failed to initialize Cosmos DB", e);
            throw new RuntimeException("Failed to initialize Cosmos DB", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
    }
}

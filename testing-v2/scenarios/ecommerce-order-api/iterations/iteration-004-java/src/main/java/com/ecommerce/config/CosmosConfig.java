package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.util.Collections;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @PostConstruct
    public void init() {
        trustAllCertificates();
    }

    private void trustAllCertificates() {
        try {
            TrustAllProvider.install();
            TrustManager[] trustAllCerts = new TrustManager[]{ TrustAllProvider.getTrustManager() };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up SSL trust", e);
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase cosmosDatabase) {
        PartitionKeyDefinition pkDef = new PartitionKeyDefinition();
        pkDef.setPaths(Collections.singletonList("/customerId"));

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("orders", pkDef);

        cosmosDatabase.createContainerIfNotExists(
                containerProperties,
                ThroughputProperties.createManualThroughput(400));

        return cosmosDatabase.getContainer("orders");
    }
}

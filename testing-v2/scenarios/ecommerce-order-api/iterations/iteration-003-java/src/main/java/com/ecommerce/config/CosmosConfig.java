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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collections;

@Configuration
public class CosmosConfig {

    private final String endpoint;
    private final String key;
    private final String databaseName;

    public CosmosConfig() {
        this.endpoint = System.getenv("COSMOS_ENDPOINT") != null
                ? System.getenv("COSMOS_ENDPOINT")
                : "https://localhost:8081";
        this.key = System.getenv("COSMOS_KEY") != null
                ? System.getenv("COSMOS_KEY")
                : "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
        this.databaseName = System.getenv("COSMOS_DATABASE") != null
                ? System.getenv("COSMOS_DATABASE")
                : "ecommerce-order-api";
    }

    @PostConstruct
    public void initSsl() {
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            try {
                TrustManager[] trustAll = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAll, new java.security.SecureRandom());
                SSLContext.setDefault(sc);
                Security.insertProviderAt(new TrustAllSslProvider(), 1);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL trust", e);
            }
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
    public CosmosDatabase cosmosDatabase(CosmosClient client) {
        client.createDatabaseIfNotExists(databaseName);
        return client.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase database) {
        PartitionKeyDefinition pkDef = new PartitionKeyDefinition();
        pkDef.setPaths(Collections.singletonList("/customerId"));
        CosmosContainerProperties props = new CosmosContainerProperties("orders", pkDef);
        database.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return database.getContainer("orders");
    }
}

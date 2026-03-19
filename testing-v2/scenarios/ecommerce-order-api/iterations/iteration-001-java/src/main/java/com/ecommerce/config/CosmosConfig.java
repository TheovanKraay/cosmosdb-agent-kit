package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    private CosmosClient cosmosClient;

    static {
        try {
            Security.insertProviderAt(new TrustAllSslProvider(), 1);
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            }, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to install trust-all SSL provider", e);
        }
    }

    @Bean
    public CosmosClient cosmosClient() {
        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();
        return this.cosmosClient;
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        CosmosDatabaseResponse response = cosmosClient.createDatabaseIfNotExists(
                databaseName,
                ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase database) {
        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties("orders", "/customerId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setAutomatic(true);

        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/*"));
        indexingPolicy.setIncludedPaths(includedPaths);

        List<List<CompositePath>> compositeIndexes = new ArrayList<>();

        List<CompositePath> statusCreatedAt = new ArrayList<>();
        CompositePath statusPath = new CompositePath();
        statusPath.setPath("/status");
        statusPath.setOrder(CompositePathSortOrder.ASCENDING);
        statusCreatedAt.add(statusPath);
        CompositePath createdAtPath1 = new CompositePath();
        createdAtPath1.setPath("/createdAt");
        createdAtPath1.setOrder(CompositePathSortOrder.DESCENDING);
        statusCreatedAt.add(createdAtPath1);
        compositeIndexes.add(statusCreatedAt);

        List<CompositePath> customerCreatedAt = new ArrayList<>();
        CompositePath customerPath = new CompositePath();
        customerPath.setPath("/customerId");
        customerPath.setOrder(CompositePathSortOrder.ASCENDING);
        customerCreatedAt.add(customerPath);
        CompositePath createdAtPath2 = new CompositePath();
        createdAtPath2.setPath("/createdAt");
        createdAtPath2.setOrder(CompositePathSortOrder.DESCENDING);
        customerCreatedAt.add(createdAtPath2);
        compositeIndexes.add(customerCreatedAt);

        indexingPolicy.setCompositeIndexes(compositeIndexes);
        containerProperties.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(containerProperties);
        return database.getContainer("orders");
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
        }
    }
}

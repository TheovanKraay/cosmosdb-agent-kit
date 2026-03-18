package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CosmosConfig {

    // Install a trust-all TrustManagerFactory provider so that Netty's JDK SSL
    // engine accepts the Cosmos DB Emulator's self-signed certificate.
    static {
        Security.insertProviderAt(new Provider("TrustAll", "1.0",
                "Trust-all TrustManagerFactory for Cosmos DB Emulator") {
            {
                put("TrustManagerFactory.PKIX", TrustAllTmfSpi.class.getName());
                put("TrustManagerFactory.SunX509", TrustAllTmfSpi.class.getName());
            }
        }, 1);
    }

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Value("${cosmos.container}")
    private String containerName;

    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(containerName, "/customerId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setAutomatic(true);

        List<IncludedPath> includedPaths = new ArrayList<>();
        includedPaths.add(new IncludedPath("/customerId/?"));
        includedPaths.add(new IncludedPath("/status/?"));
        includedPaths.add(new IncludedPath("/createdAt/?"));
        includedPaths.add(new IncludedPath("/orderId/?"));
        includedPaths.add(new IncludedPath("/type/?"));
        indexingPolicy.setIncludedPaths(includedPaths);

        List<ExcludedPath> excludedPaths = new ArrayList<>();
        excludedPaths.add(new ExcludedPath("/*"));
        indexingPolicy.setExcludedPaths(excludedPaths);

        List<List<CompositePath>> compositeIndexes = new ArrayList<>();

        List<CompositePath> statusCreatedAt = Arrays.asList(
                new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING),
                new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.DESCENDING)
        );
        compositeIndexes.add(statusCreatedAt);

        List<CompositePath> customerCreatedAt = Arrays.asList(
                new CompositePath().setPath("/customerId").setOrder(CompositePathSortOrder.ASCENDING),
                new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.DESCENDING)
        );
        compositeIndexes.add(customerCreatedAt);

        indexingPolicy.setCompositeIndexes(compositeIndexes);
        containerProperties.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(
                containerProperties,
                ThroughputProperties.createAutoscaledThroughput(4000));

        return cosmosDatabase.getContainer(containerName);
    }
}

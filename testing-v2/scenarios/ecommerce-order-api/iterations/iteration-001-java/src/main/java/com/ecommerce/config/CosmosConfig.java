package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Cosmos DB configuration using @Bean chain pattern (Rule 4.10).
 * Uses gatewayMode for emulator compatibility (Rule 4.6).
 * Enables contentResponseOnWriteEnabled (Rule 4.9).
 * CosmosClient is a singleton (Rule 4.18).
 */
@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    static {
        // Install trust-all JCA provider before any SSL connections are made.
        // The Cosmos DB Emulator uses a self-signed cert that fails Java 17 PKIX
        // signature validation even after keytool import. This installs a trust-all
        // TrustManagerFactory at JVM priority 1 so Reactor Netty's JDK SSL handler
        // accepts the emulator certificate.
        TrustAllSslConfig.install();
    }

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    /**
     * Singleton CosmosClient bean (Rule 4.18).
     * Uses gatewayMode for emulator + production compatibility (Rule 4.6).
     * Enables contentResponseOnWriteEnabled so createItem returns the document (Rule 4.9).
     * destroyMethod = "close" ensures proper cleanup.
     */
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        logger.info("Initializing CosmosClient for endpoint: {}", endpoint);

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)  // Rule 4.9: get documents back from writes
                .gatewayMode();                        // Rule 4.6: gateway mode for emulator

        return builder.buildClient();
    }

    /**
     * CosmosDatabase bean - uses parameter injection, NOT @PostConstruct (Rule 4.10).
     * Creates the database if it doesn't exist.
     */
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        logger.info("Initializing database: {}", databaseName);
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    /**
     * CosmosContainer bean for orders - uses parameter injection (Rule 4.10).
     * Partition key: /customerId - aligns with primary query pattern (Rule 2.6).
     * Custom indexing: index only the fields needed for queries (Rule 5.3).
     * Autoscale throughput for variable workloads (Rule 6.1).
     */
    @Bean
    public CosmosContainer ordersContainer(CosmosDatabase cosmosDatabase) {
        // Partition key = /customerId (Rule 2.6: align with query patterns)
        // Primary access patterns:
        //   - GET by customer:  single-partition query on customerId
        //   - GET by orderId:   cross-partition point lookup (unavoidable)
        //   - GET by status:    cross-partition (unavoidable - status is low cardinality)
        //   - GET by date range: cross-partition (unavoidable)
        CosmosContainerProperties props = new CosmosContainerProperties("orders", "/customerId");

        // Custom indexing policy: index only queried fields, exclude the rest (Rule 5.3)
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);

        // Index paths needed for queries (Rule 5.3: index only required fields)
        indexingPolicy.setIncludedPaths(Arrays.asList(
                new IncludedPath("/customerId/?"),
                new IncludedPath("/status/?"),
                new IncludedPath("/createdAt/?")
        ));

        // Cosmos DB requires "/*" in either includedPaths or excludedPaths.
        // Excluding "/*" means "exclude everything not explicitly included above" (Rule 5.3).
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/*"),
                new ExcludedPath("/items/*")
        ));

        props.setIndexingPolicy(indexingPolicy);

        // Autoscale throughput for variable e-commerce workloads (Rule 6.1)
        ThroughputProperties throughput = ThroughputProperties.createAutoscaledThroughput(4000);
        cosmosDatabase.createContainerIfNotExists(props, throughput);

        logger.info("Orders container initialized with customerId partition key");
        return cosmosDatabase.getContainer("orders");
    }
}

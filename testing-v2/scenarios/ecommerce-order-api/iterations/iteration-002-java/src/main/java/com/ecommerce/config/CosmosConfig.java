package com.ecommerce.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    private static final String DEFAULT_ENDPOINT = "https://localhost:8081";
    private static final String DEFAULT_KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
    private static final String DATABASE_NAME = "ecommerce-order-db";
    private static final String CONTAINER_NAME = "orders";
    private static final String PARTITION_KEY_PATH = "/customerId";

    private CosmosClient cosmosClient;

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = System.getenv("COSMOS_ENDPOINT") != null
                ? System.getenv("COSMOS_ENDPOINT") : DEFAULT_ENDPOINT;
        String key = System.getenv("COSMOS_KEY") != null
                ? System.getenv("COSMOS_KEY") : DEFAULT_KEY;

        logger.info("Connecting to Cosmos DB at: {}", endpoint);

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .gatewayMode()
                .buildClient();

        initializeDatabase();

        return this.cosmosClient;
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosClient client) {
        return client.getDatabase(DATABASE_NAME).getContainer(CONTAINER_NAME);
    }

    private void initializeDatabase() {
        CosmosDatabaseResponse dbResponse = cosmosClient.createDatabaseIfNotExists(DATABASE_NAME);
        CosmosDatabase database = cosmosClient.getDatabase(DATABASE_NAME);

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        indexingPolicy.setIncludedPaths(Collections.singletonList(new IncludedPath("/*")));

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(CONTAINER_NAME, PARTITION_KEY_PATH);
        containerProperties.setIndexingPolicy(indexingPolicy);

        CosmosContainerResponse containerResponse = database.createContainerIfNotExists(
                containerProperties, ThroughputProperties.createManualThroughput(400));

        logger.info("Cosmos DB initialized - database: {}, container: {}", DATABASE_NAME, CONTAINER_NAME);
    }

    @PreDestroy
    public void cleanup() {
        if (cosmosClient != null) {
            cosmosClient.close();
            logger.info("Cosmos DB client closed");
        }
    }
}

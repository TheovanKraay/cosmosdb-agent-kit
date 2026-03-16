package com.ecommerce.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {

    private static final String DATABASE_NAME = "ecommerce-order-api";
    private static final String CONTAINER_NAME = "orders";
    private static final String PARTITION_KEY = "/customerId";

    @Bean
    public CosmosClient cosmosClient() {
        String endpoint = System.getenv().getOrDefault("COSMOS_ENDPOINT", "https://localhost:8081");
        String key = System.getenv().getOrDefault("COSMOS_KEY",
                "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==");

        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .gatewayMode()
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(DATABASE_NAME);
        CosmosDatabase database = cosmosClient.getDatabase(DATABASE_NAME);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(CONTAINER_NAME, PARTITION_KEY);
        database.createContainerIfNotExists(containerProperties,
                ThroughputProperties.createManualThroughput(400));

        return database;
    }
}

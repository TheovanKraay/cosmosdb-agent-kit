package com.example.leaderboard.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    @Bean
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .buildClient();
    }

    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        // Use shared database throughput so containers inherit it
        cosmosClient.createDatabaseIfNotExists(databaseName,
                ThroughputProperties.createManualThroughput(400));
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer playersContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("players", "/region");
        // No per-container throughput; inherits from the shared database allocation
        cosmosDatabase.createContainerIfNotExists(props);
        return cosmosDatabase.getContainer("players");
    }

    @Bean
    public CosmosContainer scoresContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("scores", "/playerId");
        cosmosDatabase.createContainerIfNotExists(props);
        return cosmosDatabase.getContainer("scores");
    }
}

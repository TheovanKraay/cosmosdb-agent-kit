package com.gaming.leaderboard.config;

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
    public CosmosDatabase cosmosDatabase(CosmosClient client) {
        client.createDatabaseIfNotExists(databaseName);
        return client.getDatabase(databaseName);
    }

    @Bean(name = "playersContainer")
    public CosmosContainer playersContainer(CosmosDatabase database) {
        CosmosContainerProperties props = new CosmosContainerProperties("players", "/playerId");
        database.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return database.getContainer("players");
    }

    @Bean(name = "scoresContainer")
    public CosmosContainer scoresContainer(CosmosDatabase database) {
        CosmosContainerProperties props = new CosmosContainerProperties("scores", "/playerId");
        database.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return database.getContainer("scores");
    }
}

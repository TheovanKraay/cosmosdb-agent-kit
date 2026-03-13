package com.leaderboard.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cosmos DB configuration using dependent @Bean methods to avoid circular
 * dependency that would occur with @PostConstruct (Rule 4.10).
 *
 * Uses Gateway mode for emulator, Direct mode for production (Rule 4.12 / 4.6).
 * Enables contentResponseOnWriteEnabled so write operations return document body (Rule 4.9).
 * Singleton CosmosClient reused across the application (Rule 4.18).
 */
@Configuration
public class CosmosConfig {

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    /**
     * Singleton CosmosClient bean.
     * Uses Gateway mode when connected to emulator (localhost/127.0.0.1),
     * Direct mode otherwise (Rule 4.6 / 4.12).
     * destroyMethod = "close" ensures the client is properly closed on shutdown.
     */
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true);

        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            builder.gatewayMode();
        } else {
            builder.directMode();
        }

        return builder.buildClient();
    }

    /**
     * CosmosDatabase bean — depends on CosmosClient (Rule 4.10).
     * Creates the database if it does not exist.
     */
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    /**
     * Players container: partition key = /playerId.
     * Stores player profiles with cumulative stats.
     * Autoscale throughput to handle variable workloads (Rule 6.1).
     */
    @Bean
    public CosmosContainer playersContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props =
                new CosmosContainerProperties("players", "/playerId");
        cosmosDatabase.createContainerIfNotExists(
                props, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosDatabase.getContainer("players");
    }

    /**
     * Scores container: partition key = /playerId.
     * Stores individual score submissions. Partitioned by player to avoid
     * cross-partition writes and allow efficient per-player score history (Rule 2.2).
     */
    @Bean
    public CosmosContainer scoresContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props =
                new CosmosContainerProperties("scores", "/playerId");
        cosmosDatabase.createContainerIfNotExists(
                props, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosDatabase.getContainer("scores");
    }

    /**
     * Leaderboard entries container: partition key = /leaderboardKey.
     * Materialized view pattern (Rule 9.1): each player has one entry per leaderboard
     * ("global" or region code). Queries within a single partition avoid cross-partition
     * fan-out for the frequent top-N and rank lookups.
     */
    @Bean
    public CosmosContainer leaderboardContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props =
                new CosmosContainerProperties("leaderboard-entries", "/leaderboardKey");
        cosmosDatabase.createContainerIfNotExists(
                props, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosDatabase.getContainer("leaderboard-entries");
    }
}

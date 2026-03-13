package com.example.leaderboard.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class CosmosConfig {

    private static final Logger logger = LoggerFactory.getLogger(CosmosConfig.class);

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    /**
     * Singleton CosmosClient bean.
     * - Uses Gateway mode for emulator (localhost), Direct mode for production.
     * - contentResponseOnWriteEnabled(true) ensures getItem() is non-null after writes.
     * - destroyMethod = "close" ensures proper cleanup on shutdown.
     */
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
        logger.info("Connecting to Cosmos DB at: {}", endpoint);

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true);

        // Use Gateway mode for the emulator (Direct mode has SSL issues with emulator).
        // Use Direct mode for production for better performance.
        if (isEmulator) {
            builder.gatewayMode(GatewayConnectionConfig.getDefaultConfig());
            logger.info("Using Gateway connection mode (emulator detected)");
        } else {
            builder.directMode(DirectConnectionConfig.getDefaultConfig());
            logger.info("Using Direct connection mode (production)");
        }

        return builder.buildClient();
    }

    /**
     * CosmosDatabase bean — depends on CosmosClient.
     * Creates the database if it does not exist.
     */
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    /**
     * Players container — partitioned by /playerId (high-cardinality, avoids hot partitions).
     */
    @Bean
    public CosmosContainer playersContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("players", "/playerId");
        cosmosDatabase.createContainerIfNotExists(
                props, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosDatabase.getContainer("players");
    }

    /**
     * Scores container — partitioned by /playerId so all scores for a player
     * are co-located (efficient per-player history queries, no hot partitions).
     */
    @Bean
    public CosmosContainer scoresContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("scores", "/playerId");
        cosmosDatabase.createContainerIfNotExists(
                props, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosDatabase.getContainer("scores");
    }

    /**
     * Leaderboard container — materialized view of top scores, partitioned by /leaderboardKey.
     * leaderboardKey is "global" for the global leaderboard, or "region-{region}" for regional.
     * All top-N queries are single-partition, with a composite index on /bestScore DESC.
     */
    @Bean
    public CosmosContainer leaderboardContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("leaderboard", "/leaderboardKey");

        // Composite index so ORDER BY bestScore DESC works efficiently within a partition.
        // Only /bestScore is needed — /leaderboardKey is the partition key and must not
        // be included in composite index paths.
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Collections.singletonList(new IncludedPath("/*")));

        CompositePath bestScoreDesc = new CompositePath();
        bestScoreDesc.setPath("/bestScore");
        bestScoreDesc.setOrder(CompositePathSortOrder.DESCENDING);

        indexingPolicy.setCompositeIndexes(Collections.singletonList(
                Collections.singletonList(bestScoreDesc)));
        props.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(
                props, ThroughputProperties.createAutoscaledThroughput(4000));
        return cosmosDatabase.getContainer("leaderboard");
    }
}

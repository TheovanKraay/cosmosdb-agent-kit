package com.example.leaderboard.config;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Cosmos DB configuration following best practices:
 * - Rule 4.18: CosmosClient as singleton
 * - Rule 4.10: Dependent @Bean chain for initialization (no @PostConstruct)
 * - Rule 4.6:  Gateway mode for local emulator, Direct mode for production
 * - Rule 4.9:  Enable contentResponseOnWriteEnabled
 * - Rule 5.2:  Composite index for ORDER BY on leaderboard container
 */
@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    // Rule 4.18: Singleton client — destroyMethod ensures clean shutdown
    // Rule 4.9:  contentResponseOnWriteEnabled so createItem/upsertItem return the document
    // Rule 4.6:  Gateway mode for emulator (Direct mode has SSL issues with emulator)
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        boolean isLocal = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        CosmosClientBuilder builder = new CosmosClientBuilder()
            .endpoint(endpoint)
            .key(key)
            .consistencyLevel(ConsistencyLevel.SESSION)
            .contentResponseOnWriteEnabled(true);

        if (isLocal) {
            // Rule 4.6: Gateway mode required for Cosmos Emulator
            builder.gatewayMode();
        } else {
            builder.directMode();
        }

        return builder.buildClient();
    }

    // Rule 4.10: Dependent @Bean with parameter injection — no circular dependency
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer playersContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("players", "/playerId");

        // Rule 5.4: Explicit consistent indexing mode
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        props.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return cosmosDatabase.getContainer("players");
    }

    @Bean
    public CosmosContainer scoresContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("scores", "/playerId");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        props.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return cosmosDatabase.getContainer("scores");
    }

    // Rule 9.1: Leaderboard container as materialized view, partitioned by /leaderboardKey
    // Rule 5.2/5.1: Composite index supporting ORDER BY c.score DESC queries
    @Bean
    public CosmosContainer leaderboardContainer(CosmosDatabase cosmosDatabase) {
        CosmosContainerProperties props = new CosmosContainerProperties("leaderboard", "/leaderboardKey");

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);

        // Composite index for ORDER BY c.score DESC (within partition queries)
        CompositePath scoreDescPath = new CompositePath();
        scoreDescPath.setPath("/score");
        scoreDescPath.setOrder(CompositePathSortOrder.DESCENDING);
        indexingPolicy.setCompositeIndexes(Collections.singletonList(
            Collections.singletonList(scoreDescPath)
        ));

        props.setIndexingPolicy(indexingPolicy);

        cosmosDatabase.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return cosmosDatabase.getContainer("leaderboard");
    }
}

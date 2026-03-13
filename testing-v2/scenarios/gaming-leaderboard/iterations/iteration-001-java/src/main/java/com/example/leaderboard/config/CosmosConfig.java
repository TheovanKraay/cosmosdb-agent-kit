package com.example.leaderboard.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public CosmosContainer playersContainer(CosmosDatabase database) {
        CosmosContainerProperties props = new CosmosContainerProperties("players", "/playerId");

        // Add composite indexes for leaderboard ORDER BY queries
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);

        // Composite index for: ORDER BY c.bestScore DESC (global leaderboard)
        CompositePath bestScorePath = new CompositePath();
        bestScorePath.setPath("/bestScore");
        bestScorePath.setOrder(CompositePathSortOrder.DESCENDING);
        List<CompositePath> globalIndex = Arrays.asList(bestScorePath);

        // Composite index for: WHERE c.region = ? ORDER BY c.bestScore DESC (regional leaderboard)
        CompositePath regionPath = new CompositePath();
        regionPath.setPath("/region");
        regionPath.setOrder(CompositePathSortOrder.ASCENDING);
        CompositePath bestScoreDescPath = new CompositePath();
        bestScoreDescPath.setPath("/bestScore");
        bestScoreDescPath.setOrder(CompositePathSortOrder.DESCENDING);
        List<CompositePath> regionalIndex = Arrays.asList(regionPath, bestScoreDescPath);

        indexingPolicy.setCompositeIndexes(Arrays.asList(globalIndex, regionalIndex));
        props.setIndexingPolicy(indexingPolicy);

        database.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return database.getContainer("players");
    }

    @Bean
    public CosmosContainer scoresContainer(CosmosDatabase database) {
        CosmosContainerProperties props = new CosmosContainerProperties("scores", "/playerId");
        database.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
        return database.getContainer("scores");
    }
}

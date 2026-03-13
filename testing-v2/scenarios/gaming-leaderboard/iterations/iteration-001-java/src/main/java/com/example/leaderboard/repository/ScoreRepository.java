package com.example.leaderboard.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.*;
import com.example.leaderboard.model.Score;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/**
 * Data access for score documents.
 * Partition key: /playerId — distributes write load evenly across players.
 */
@Repository
public class ScoreRepository {

    private final CosmosContainer container;

    public ScoreRepository(@Qualifier("scoresContainer") CosmosContainer container) {
        this.container = container;
    }

    // Rule 4.9: contentResponseOnWriteEnabled set at client level
    public Score save(Score score) {
        CosmosItemResponse<Score> response = container.createItem(
            score,
            new PartitionKey(score.getPlayerId()),
            new CosmosItemRequestOptions());
        return response.getItem();
    }
}

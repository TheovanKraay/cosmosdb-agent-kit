package com.gaming.leaderboard.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.gaming.leaderboard.model.Score;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class ScoreRepository {

    private final CosmosContainer scoresContainer;

    public ScoreRepository(@Qualifier("scoresContainer") CosmosContainer scoresContainer) {
        this.scoresContainer = scoresContainer;
    }

    public Score createScore(Score score) {
        scoresContainer.createItem(score, new PartitionKey(score.getPlayerId()), new CosmosItemRequestOptions());
        return score;
    }
}

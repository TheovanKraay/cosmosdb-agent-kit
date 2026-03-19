package com.gaming.leaderboard.repository;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.gaming.leaderboard.model.GameScore;
import org.springframework.stereotype.Repository;

/**
 * Score repository using Spring Data Cosmos.
 * 
 * Partition key: playerId — efficient for "get all scores for a player" queries.
 */
@Repository
public interface ScoreRepository extends CosmosRepository<GameScore, String> {
}

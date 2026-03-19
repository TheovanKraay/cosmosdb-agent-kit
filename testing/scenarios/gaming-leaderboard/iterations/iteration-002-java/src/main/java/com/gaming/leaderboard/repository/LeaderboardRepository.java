package com.gaming.leaderboard.repository;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.gaming.leaderboard.model.LeaderboardEntry;
import org.springframework.stereotype.Repository;

/**
 * Leaderboard repository using Spring Data Cosmos.
 * 
 * Partition key: partitionKey (synthetic: scope + "_" + weekPeriod)
 * Rule 9.1: Materialized view for efficient leaderboard reads
 */
@Repository
public interface LeaderboardRepository extends CosmosRepository<LeaderboardEntry, String> {
}

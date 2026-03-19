package com.gaming.leaderboard.repository;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.gaming.leaderboard.model.Player;
import org.springframework.stereotype.Repository;

/**
 * Player repository using Spring Data Cosmos.
 * 
 * Rule 4.16: Spring Data Cosmos handles singleton CosmosClient automatically.
 * Rule 4.9: Spring Data Cosmos returns saved entities automatically.
 */
@Repository
public interface PlayerRepository extends CosmosRepository<Player, String> {
}

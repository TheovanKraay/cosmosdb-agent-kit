package com.example.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.example.leaderboard.model.Player;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlayerService {

    private final CosmosContainer playersContainer;

    public PlayerService(@Qualifier("playersContainer") CosmosContainer playersContainer) {
        this.playersContainer = playersContainer;
    }

    /**
     * Create a new player profile. Returns 409 if the player already exists.
     */
    public Player createPlayer(String playerId, String displayName, String region) {
        Player player = new Player(playerId, displayName, region);
        try {
            CosmosItemRequestOptions options = new CosmosItemRequestOptions();
            CosmosItemResponse<Player> response = playersContainer.createItem(
                    player,
                    new PartitionKey(playerId),
                    options);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Player already exists: " + playerId);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create player: " + e.getMessage());
        }
    }

    /**
     * Get a player by ID. Returns 404 if not found.
     */
    public Player getPlayer(String playerId) {
        try {
            CosmosItemResponse<Player> response = playersContainer.readItem(
                    playerId, new PartitionKey(playerId), Player.class);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Player not found: " + playerId);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to get player: " + e.getMessage());
        }
    }

    /**
     * Update a player's stats after a score submission.
     * Uses upsert so stats are always consistent.
     */
    public Player updatePlayerStats(Player player, int newScore) {
        player.setTotalGames(player.getTotalGames() + 1);
        player.setTotalScore(player.getTotalScore() + newScore);
        player.setAverageScore((double) player.getTotalScore() / player.getTotalGames());
        if (newScore > player.getBestScore()) {
            player.setBestScore(newScore);
        }

        try {
            CosmosItemResponse<Player> response = playersContainer.upsertItem(
                    player,
                    new PartitionKey(player.getPlayerId()),
                    new CosmosItemRequestOptions());
            return response.getItem();
        } catch (CosmosException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update player stats: " + e.getMessage());
        }
    }
}

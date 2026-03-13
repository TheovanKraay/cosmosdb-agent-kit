package com.example.leaderboard.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.*;
import com.example.leaderboard.model.Player;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for player profile documents.
 * Uses point reads (O(1)) when playerId is known — id == playerId.
 */
@Repository
public class PlayerRepository {

    private final CosmosContainer container;

    public PlayerRepository(@Qualifier("playersContainer") CosmosContainer container) {
        this.container = container;
    }

    public Optional<Player> findByPlayerId(String playerId) {
        try {
            CosmosItemResponse<Player> response = container.readItem(
                playerId, new PartitionKey(playerId), Player.class);
            return Optional.ofNullable(response.getItem());
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // Rule 4.9: contentResponseOnWriteEnabled set at client level, so getItem() is non-null
    public Player save(Player player) {
        CosmosItemResponse<Player> response = container.upsertItem(
            player,
            new PartitionKey(player.getPlayerId()),
            new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Player create(Player player) {
        CosmosItemResponse<Player> response = container.createItem(
            player,
            new PartitionKey(player.getPlayerId()),
            new CosmosItemRequestOptions());
        return response.getItem();
    }
}

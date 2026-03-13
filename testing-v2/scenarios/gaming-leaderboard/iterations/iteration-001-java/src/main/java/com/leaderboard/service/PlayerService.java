package com.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.leaderboard.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);

    private final CosmosContainer playersContainer;

    @Autowired
    public PlayerService(@Qualifier("playersContainer") CosmosContainer playersContainer) {
        this.playersContainer = playersContainer;
    }

    /**
     * Create a new player. Returns the persisted player document.
     * Uses parameterized point write — no cross-partition operations (Rule 2.2).
     */
    public Player createPlayer(String playerId, String displayName, String region) {
        Player player = new Player(playerId, displayName, region);
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        CosmosItemResponse<Player> response =
                playersContainer.createItem(player, new PartitionKey(playerId), options);
        Player created = response.getItem();
        if (created == null) {
            created = player;
        }
        logger.info("Created player: {}", playerId);
        return created;
    }

    /**
     * Retrieve a player by ID using a point read (O(1), single RU cost).
     * Throws 404 if not found.
     */
    public Player getPlayer(String playerId) {
        try {
            CosmosItemResponse<Player> response =
                    playersContainer.readItem(playerId, new PartitionKey(playerId), Player.class);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Player not found: " + playerId);
            }
            throw e;
        }
    }

    /**
     * Update player stats after a score submission.
     * Uses ETag for optimistic concurrency to prevent lost updates on concurrent
     * read-modify-write operations (Rule 4.7). Falls back to plain upsert if no
     * ETag is available (e.g. first write).
     */
    public Player updatePlayerStats(Player player) {
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        if (player.getEtag() != null && !player.getEtag().isEmpty()) {
            options.setIfMatchETag(player.getEtag());
        }
        try {
            CosmosItemResponse<Player> response =
                    playersContainer.upsertItem(player, new PartitionKey(player.getPlayerId()), options);
            Player updated = response.getItem();
            return updated != null ? updated : player;
        } catch (CosmosException e) {
            if (e.getStatusCode() == 412) {
                // Precondition failed — another write updated the player concurrently.
                // Re-read the latest version and re-apply the update.
                Player latest = getPlayer(player.getPlayerId());
                latest.setTotalGames(player.getTotalGames());
                latest.setAverageScore(player.getAverageScore());
                if (player.getBestScore() > latest.getBestScore()) {
                    latest.setBestScore(player.getBestScore());
                }
                CosmosItemRequestOptions retryOptions = new CosmosItemRequestOptions();
                CosmosItemResponse<Player> retryResponse =
                        playersContainer.upsertItem(latest, new PartitionKey(latest.getPlayerId()), retryOptions);
                Player updated = retryResponse.getItem();
                return updated != null ? updated : latest;
            }
            throw e;
        }
    }
}

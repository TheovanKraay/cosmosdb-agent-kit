package com.example.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.Score;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class ScoreService {

    private static final String GLOBAL_LEADERBOARD_KEY = "global";
    private static final String REGIONAL_LEADERBOARD_PREFIX = "region-";

    private final CosmosContainer scoresContainer;
    private final PlayerService playerService;
    private final LeaderboardService leaderboardService;

    public ScoreService(
            @Qualifier("scoresContainer") CosmosContainer scoresContainer,
            PlayerService playerService,
            LeaderboardService leaderboardService) {
        this.scoresContainer = scoresContainer;
        this.playerService = playerService;
        this.leaderboardService = leaderboardService;
    }

    /**
     * Submit a new score for a player.
     * 1. Validates the player exists.
     * 2. Persists the raw score document (partition key = playerId).
     * 3. Updates the player's cumulative stats.
     * 4. Updates the global and regional leaderboard materialized views.
     */
    public Score submitScore(String playerId, int scoreValue, String gameMode) {
        // Validate player exists first
        Player player = playerService.getPlayer(playerId); // throws 404 if not found

        // Persist the raw score document
        String scoreId = UUID.randomUUID().toString();
        Score score = new Score(scoreId, playerId, scoreValue, gameMode, Instant.now().toString());

        try {
            CosmosItemResponse<Score> response = scoresContainer.createItem(
                    score,
                    new PartitionKey(playerId),
                    new CosmosItemRequestOptions());
            Score created = response.getItem();

            // Update player stats (totalGames, bestScore, averageScore)
            Player updatedPlayer = playerService.updatePlayerStats(player, scoreValue);

            // Update materialized leaderboard views for global and regional
            leaderboardService.upsertLeaderboardEntry(
                    GLOBAL_LEADERBOARD_KEY,
                    playerId,
                    updatedPlayer.getDisplayName(),
                    updatedPlayer.getBestScore());

            String regionalKey = REGIONAL_LEADERBOARD_PREFIX + updatedPlayer.getRegion();
            leaderboardService.upsertLeaderboardEntry(
                    regionalKey,
                    playerId,
                    updatedPlayer.getDisplayName(),
                    updatedPlayer.getBestScore());

            return created;
        } catch (CosmosException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to submit score: " + e.getMessage());
        }
    }

    public static String globalLeaderboardKey() {
        return GLOBAL_LEADERBOARD_KEY;
    }

    public static String regionalLeaderboardKey(String region) {
        return REGIONAL_LEADERBOARD_PREFIX + region;
    }
}

package com.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.leaderboard.dto.ScoreResponse;
import com.leaderboard.model.Player;
import com.leaderboard.model.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class ScoreService {

    private static final Logger logger = LoggerFactory.getLogger(ScoreService.class);

    private final CosmosContainer scoresContainer;
    private final PlayerService playerService;
    private final LeaderboardService leaderboardService;

    @Autowired
    public ScoreService(@Qualifier("scoresContainer") CosmosContainer scoresContainer,
                        PlayerService playerService,
                        LeaderboardService leaderboardService) {
        this.scoresContainer = scoresContainer;
        this.playerService = playerService;
        this.leaderboardService = leaderboardService;
    }

    /**
     * Submit a score for a player. Steps:
     * 1. Validate the player exists.
     * 2. Persist the score document (partition key = playerId, high-cardinality — Rule 2.4).
     * 3. Update player cumulative stats (totalGames, averageScore, bestScore).
     * 4. If the new score is a new best, upsert materialized leaderboard entries (Rule 9.1).
     *
     * Score IDs are UUID-based to avoid forbidden characters in doc IDs (Rule 1.4).
     */
    public ScoreResponse submitScore(String playerId, int score, String gameMode) {
        // Validate player exists (point read — O(1))
        Player player;
        try {
            player = playerService.getPlayer(playerId);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Player not found: " + playerId);
            }
            throw e;
        }

        // Persist score document
        // UUID id avoids special characters in id field (Rule 1.4)
        String scoreId = UUID.randomUUID().toString();
        Score scoreDoc = new Score(scoreId, playerId, score, gameMode, Instant.now().toString());
        scoresContainer.createItem(scoreDoc, new PartitionKey(playerId),
                new CosmosItemRequestOptions());
        logger.info("Saved score: id={} player={} score={}", scoreId, playerId, score);

        // Update player stats
        boolean isNewBest = score > player.getBestScore();
        int newTotalGames = player.getTotalGames() + 1;
        double newAverage = ((player.getAverageScore() * player.getTotalGames()) + score)
                / newTotalGames;

        player.setTotalGames(newTotalGames);
        player.setAverageScore(newAverage);
        if (isNewBest) {
            player.setBestScore(score);
        }
        playerService.updatePlayerStats(player);

        // Update materialized leaderboard if new personal best (Rule 9.1)
        if (isNewBest) {
            leaderboardService.upsertLeaderboardEntries(
                    playerId, player.getDisplayName(), score, player.getRegion());
        }

        return new ScoreResponse(scoreId, playerId, score);
    }
}

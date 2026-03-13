package com.example.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.PlayerRankResponse;
import com.example.leaderboard.model.ScoreRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LeaderboardService {

    private final CosmosContainer playersContainer;
    private final CosmosContainer scoresContainer;

    public LeaderboardService(
            @Qualifier("playersContainer") CosmosContainer playersContainer,
            @Qualifier("scoresContainer") CosmosContainer scoresContainer) {
        this.playersContainer = playersContainer;
        this.scoresContainer = scoresContainer;
    }

    // -------------------------------------------------------------------------
    // Player operations
    // -------------------------------------------------------------------------

    public Player createPlayer(String playerId, String displayName, String region) {
        Player player = new Player(playerId, displayName, region);
        playersContainer.createItem(player, new PartitionKey(playerId), new CosmosItemRequestOptions());
        return player;
    }

    public Optional<Player> getPlayer(String playerId) {
        try {
            CosmosItemResponse<Player> response = playersContainer.readItem(
                    playerId, new PartitionKey(playerId), Player.class);
            return Optional.of(response.getItem());
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Score operations
    // -------------------------------------------------------------------------

    public ScoreRecord submitScore(String playerId, int score, String gameMode) {
        String scoreId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        ScoreRecord scoreRecord = new ScoreRecord(scoreId, playerId, score, gameMode, timestamp);
        scoresContainer.createItem(scoreRecord, new PartitionKey(playerId), new CosmosItemRequestOptions());

        // Update player stats if player exists
        try {
            CosmosItemResponse<Player> response = playersContainer.readItem(
                    playerId, new PartitionKey(playerId), Player.class);
            Player player = response.getItem();
            double newAverage = (player.getAverageScore() * player.getTotalGames() + score)
                    / (player.getTotalGames() + 1);
            player.setTotalGames(player.getTotalGames() + 1);
            player.setBestScore(Math.max(player.getBestScore(), score));
            player.setAverageScore(newAverage);
            playersContainer.upsertItem(player, new PartitionKey(playerId), new CosmosItemRequestOptions());
        } catch (CosmosException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }
            // Player not found - score still recorded but stats not updated
        }

        return scoreRecord;
    }

    // -------------------------------------------------------------------------
    // Leaderboard operations
    // -------------------------------------------------------------------------

    public List<LeaderboardEntry> getGlobalLeaderboard(int top) {
        String query = "SELECT c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC OFFSET 0 LIMIT @top";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@top", top)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Player> results = playersContainer.queryItems(querySpec, options, Player.class);

        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        int rank = 1;
        for (Player p : results) {
            leaderboard.add(new LeaderboardEntry(rank++, p.getPlayerId(), p.getDisplayName(), p.getBestScore()));
        }
        return leaderboard;
    }

    public List<LeaderboardEntry> getRegionalLeaderboard(String region, int top) {
        String query = "SELECT c.playerId, c.displayName, c.bestScore FROM c WHERE c.region = @region ORDER BY c.bestScore DESC OFFSET 0 LIMIT @top";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                        new SqlParameter("@region", region),
                        new SqlParameter("@top", top)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Player> results = playersContainer.queryItems(querySpec, options, Player.class);

        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        int rank = 1;
        for (Player p : results) {
            leaderboard.add(new LeaderboardEntry(rank++, p.getPlayerId(), p.getDisplayName(), p.getBestScore()));
        }
        return leaderboard;
    }

    // -------------------------------------------------------------------------
    // Player rank
    // -------------------------------------------------------------------------

    public Optional<PlayerRankResponse> getPlayerRank(String playerId) {
        Optional<Player> playerOpt = getPlayer(playerId);
        if (playerOpt.isEmpty()) {
            return Optional.empty();
        }
        Player player = playerOpt.get();

        if (player.getTotalGames() == 0) {
            return Optional.empty();
        }

        // Count players with a better score to determine rank
        String countQuery = "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score";
        SqlQuerySpec countSpec = new SqlQuerySpec(countQuery,
                Arrays.asList(new SqlParameter("@score", player.getBestScore())));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Long> countResults = playersContainer.queryItems(countSpec, options, Long.class);

        long higherCount = 0;
        for (Long count : countResults) {
            higherCount = count;
        }
        int rank = (int) higherCount + 1;

        // Fetch neighbors: players ranked rank-10 to rank+10
        int startOffset = Math.max(0, rank - 11);
        int limit = 21;
        String neighborsQuery = "SELECT c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC OFFSET @offset LIMIT @limit";
        SqlQuerySpec neighborsSpec = new SqlQuerySpec(neighborsQuery,
                Arrays.asList(
                        new SqlParameter("@offset", startOffset),
                        new SqlParameter("@limit", limit)));

        CosmosPagedIterable<Player> neighborResults = playersContainer.queryItems(neighborsSpec, options, Player.class);

        List<LeaderboardEntry> neighbors = new ArrayList<>();
        int neighborRank = startOffset + 1;
        for (Player p : neighborResults) {
            if (!p.getPlayerId().equals(playerId)) {
                neighbors.add(new LeaderboardEntry(neighborRank, p.getPlayerId(), p.getDisplayName(), p.getBestScore()));
            }
            neighborRank++;
        }

        return Optional.of(new PlayerRankResponse(playerId, rank, player.getBestScore(), neighbors));
    }
}

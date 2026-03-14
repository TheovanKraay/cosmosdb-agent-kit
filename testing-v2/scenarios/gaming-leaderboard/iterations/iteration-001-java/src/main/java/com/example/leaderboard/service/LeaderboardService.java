package com.example.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.PlayerRankResponse;
import com.example.leaderboard.model.Score;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    public Player createPlayer(String playerId, String displayName, String region) {
        Player player = new Player(playerId, displayName, region);
        playersContainer.createItem(player, new PartitionKey(region), new CosmosItemRequestOptions());
        return player;
    }

    public Player getPlayer(String playerId) {
        String query = "SELECT * FROM c WHERE c.playerId = @playerId";
        SqlQuerySpec spec = new SqlQuerySpec(query, List.of(new SqlParameter("@playerId", playerId)));

        CosmosPagedIterable<Player> results = playersContainer.queryItems(spec,
                new CosmosQueryRequestOptions(), Player.class);

        for (Player player : results) {
            return player;
        }
        return null;
    }

    public Score submitScore(String playerId, int score, String gameMode) {
        Player player = getPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + playerId);
        }

        String scoreId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        Score scoreRecord = new Score(scoreId, playerId, score, gameMode, timestamp);
        scoresContainer.createItem(scoreRecord, new PartitionKey(playerId), new CosmosItemRequestOptions());

        player.setTotalGames(player.getTotalGames() + 1);
        player.setTotalScore(player.getTotalScore() + score);
        if (score > player.getBestScore()) {
            player.setBestScore(score);
        }
        player.setAverageScore((double) player.getTotalScore() / player.getTotalGames());
        playersContainer.upsertItem(player, new PartitionKey(player.getRegion()), new CosmosItemRequestOptions());

        return scoreRecord;
    }

    public List<LeaderboardEntry> getGlobalLeaderboard(int top) {
        // TOP does not support parameters in Cosmos DB SQL; embed as literal
        String query = String.format(
                "SELECT TOP %d c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC", top);

        CosmosPagedIterable<JsonNode> results = playersContainer.queryItems(
                new SqlQuerySpec(query), new CosmosQueryRequestOptions(), JsonNode.class);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (JsonNode node : results) {
            entries.add(new LeaderboardEntry(rank++,
                    node.path("playerId").asText(),
                    node.path("displayName").asText(),
                    node.path("bestScore").asInt()));
        }
        return entries;
    }

    public List<LeaderboardEntry> getRegionalLeaderboard(String region, int top) {
        String query = String.format(
                "SELECT TOP %d c.playerId, c.displayName, c.bestScore FROM c WHERE c.region = @region ORDER BY c.bestScore DESC",
                top);
        SqlQuerySpec spec = new SqlQuerySpec(query, List.of(new SqlParameter("@region", region)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(region));

        CosmosPagedIterable<JsonNode> results = playersContainer.queryItems(spec, options, JsonNode.class);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (JsonNode node : results) {
            entries.add(new LeaderboardEntry(rank++,
                    node.path("playerId").asText(),
                    node.path("displayName").asText(),
                    node.path("bestScore").asInt()));
        }
        return entries;
    }

    public PlayerRankResponse getPlayerRank(String playerId) {
        Player player = getPlayer(playerId);
        if (player == null || player.getTotalGames() == 0) {
            return null;
        }

        // Count how many players have a higher bestScore to determine rank
        String countQuery = "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score";
        SqlQuerySpec countSpec = new SqlQuerySpec(countQuery,
                List.of(new SqlParameter("@score", player.getBestScore())));

        CosmosPagedIterable<Integer> countResults = playersContainer.queryItems(
                countSpec, new CosmosQueryRequestOptions(), Integer.class);

        int rank = 1;
        for (Integer count : countResults) {
            rank = count + 1;
        }

        // Fetch enough players to populate ±10 neighbors around the player's rank
        int windowStart = Math.max(1, rank - 10);
        int fetchTop = rank + 10;

        String neighborsQuery = String.format(
                "SELECT TOP %d c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC", fetchTop);

        CosmosPagedIterable<JsonNode> neighborResults = playersContainer.queryItems(
                new SqlQuerySpec(neighborsQuery), new CosmosQueryRequestOptions(), JsonNode.class);

        List<LeaderboardEntry> allEntries = new ArrayList<>();
        int r = 1;
        for (JsonNode node : neighborResults) {
            allEntries.add(new LeaderboardEntry(r++,
                    node.path("playerId").asText(),
                    node.path("displayName").asText(),
                    node.path("bestScore").asInt()));
        }

        // Include all entries in the window except the player themselves
        List<LeaderboardEntry> neighbors = new ArrayList<>();
        for (LeaderboardEntry entry : allEntries) {
            if (entry.getRank() >= windowStart && entry.getRank() <= rank + 10
                    && !entry.getPlayerId().equals(playerId)) {
                neighbors.add(entry);
            }
        }

        return new PlayerRankResponse(playerId, rank, player.getBestScore(), neighbors);
    }
}

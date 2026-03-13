package com.example.leaderboard.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.PlayerRankResponse;
import com.example.leaderboard.model.Score;
import com.example.leaderboard.model.ScoreResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CosmosRepository {

    private final CosmosContainer playersContainer;
    private final CosmosContainer scoresContainer;

    @Autowired
    public CosmosRepository(CosmosContainer playersContainer, CosmosContainer scoresContainer) {
        this.playersContainer = playersContainer;
        this.scoresContainer = scoresContainer;
    }

    public Player createPlayer(Player player) {
        player.setId(player.getPlayerId());
        player.setTotalGames(0);
        player.setBestScore(0);
        player.setAverageScore(0.0);
        player.setTotalScore(0L);
        playersContainer.createItem(player, new PartitionKey(player.getPlayerId()), new CosmosItemRequestOptions());
        return player;
    }

    public Optional<Player> getPlayer(String playerId) {
        try {
            Player player = playersContainer
                    .readItem(playerId, new PartitionKey(playerId), Player.class)
                    .getItem();
            return Optional.ofNullable(player);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public ScoreResponse submitScore(String playerId, int score, String gameMode) {
        // Create the score document
        Score scoreDoc = new Score();
        String scoreId = UUID.randomUUID().toString();
        scoreDoc.setId(scoreId);
        scoreDoc.setPlayerId(playerId);
        scoreDoc.setScore(score);
        scoreDoc.setGameMode(gameMode);
        scoreDoc.setTimestamp(Instant.now().toString());
        scoresContainer.createItem(scoreDoc, new PartitionKey(playerId), new CosmosItemRequestOptions());

        // Update player stats (read-modify-write)
        Optional<Player> playerOpt = getPlayer(playerId);
        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            player.setTotalGames(player.getTotalGames() + 1);
            long newTotalScore = player.getTotalScore() + score;
            player.setTotalScore(newTotalScore);
            if (score > player.getBestScore()) {
                player.setBestScore(score);
            }
            player.setAverageScore((double) newTotalScore / player.getTotalGames());
            playersContainer.upsertItem(player, new PartitionKey(player.getPlayerId()), new CosmosItemRequestOptions());
        }

        return new ScoreResponse(scoreId, playerId, score);
    }

    public List<LeaderboardEntry> getGlobalLeaderboard(int top) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore AS score FROM c ORDER BY c.bestScore DESC OFFSET 0 LIMIT @top",
                Arrays.asList(new SqlParameter("@top", top))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<LeaderboardQuery> results = playersContainer
                .queryItems(querySpec, options, LeaderboardQuery.class)
                .stream()
                .collect(Collectors.toList());

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            LeaderboardQuery r = results.get(i);
            entries.add(new LeaderboardEntry(i + 1, r.getPlayerId(), r.getDisplayName(), r.getScore()));
        }
        return entries;
    }

    public List<LeaderboardEntry> getRegionalLeaderboard(String region, int top) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore AS score FROM c WHERE c.region = @region ORDER BY c.bestScore DESC OFFSET 0 LIMIT @top",
                Arrays.asList(
                        new SqlParameter("@region", region),
                        new SqlParameter("@top", top)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<LeaderboardQuery> results = playersContainer
                .queryItems(querySpec, options, LeaderboardQuery.class)
                .stream()
                .collect(Collectors.toList());

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            LeaderboardQuery r = results.get(i);
            entries.add(new LeaderboardEntry(i + 1, r.getPlayerId(), r.getDisplayName(), r.getScore()));
        }
        return entries;
    }

    public Optional<PlayerRankResponse> getPlayerRank(String playerId) {
        Optional<Player> playerOpt = getPlayer(playerId);
        if (playerOpt.isEmpty()) {
            return Optional.empty();
        }

        Player player = playerOpt.get();
        if (player.getTotalGames() == 0) {
            return Optional.empty();
        }

        int playerBestScore = player.getBestScore();

        // Count players with a higher bestScore to determine rank
        SqlQuerySpec countSpec = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score",
                Arrays.asList(new SqlParameter("@score", playerBestScore))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        long playersAbove = playersContainer
                .queryItems(countSpec, options, Long.class)
                .stream()
                .findFirst()
                .orElse(0L);

        int rank = (int) playersAbove + 1;

        // Fetch neighbors: players at positions (rank-10) to (rank+10)
        int offset = Math.max(0, rank - 11);
        int limit = 21;

        SqlQuerySpec neighborsSpec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore AS score FROM c ORDER BY c.bestScore DESC OFFSET @offset LIMIT @limit",
                Arrays.asList(
                        new SqlParameter("@offset", offset),
                        new SqlParameter("@limit", limit)
                )
        );

        List<LeaderboardQuery> neighborResults = playersContainer
                .queryItems(neighborsSpec, options, LeaderboardQuery.class)
                .stream()
                .collect(Collectors.toList());

        List<LeaderboardEntry> neighbors = new ArrayList<>();
        for (int i = 0; i < neighborResults.size(); i++) {
            LeaderboardQuery r = neighborResults.get(i);
            neighbors.add(new LeaderboardEntry(offset + i + 1, r.getPlayerId(), r.getDisplayName(), r.getScore()));
        }

        PlayerRankResponse response = new PlayerRankResponse();
        response.setPlayerId(playerId);
        response.setRank(rank);
        response.setScore(playerBestScore);
        response.setNeighbors(neighbors);
        return Optional.of(response);
    }

    // Internal class for deserializing leaderboard query results
    public static class LeaderboardQuery {
        private String playerId;
        private String displayName;
        private int score;

        public LeaderboardQuery() {}

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
    }
}

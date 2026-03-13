package com.gaming.leaderboard.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.gaming.leaderboard.model.LeaderboardEntry;
import com.gaming.leaderboard.model.Player;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

@Repository
public class PlayerRepository {

    private final CosmosContainer playersContainer;

    public PlayerRepository(@Qualifier("playersContainer") CosmosContainer playersContainer) {
        this.playersContainer = playersContainer;
    }

    public Player createPlayer(Player player) {
        playersContainer.createItem(player, new PartitionKey(player.getPlayerId()), new CosmosItemRequestOptions());
        return player;
    }

    public Player getPlayer(String playerId) {
        try {
            return playersContainer.readItem(playerId, new PartitionKey(playerId), Player.class).getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public Player updatePlayer(Player player) {
        playersContainer.upsertItem(player, new PartitionKey(player.getPlayerId()), new CosmosItemRequestOptions());
        return player;
    }

    public List<LeaderboardEntry> getGlobalLeaderboard(int top) {
        SqlQuerySpec spec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC OFFSET 0 LIMIT @top",
                Arrays.asList(new SqlParameter("@top", top)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<LeaderboardEntry> entries = new ArrayList<>();
        int[] rank = {1};
        playersContainer.queryItems(spec, options, PlayerProjection.class)
                .forEach(p -> entries.add(new LeaderboardEntry(rank[0]++, p.getPlayerId(), p.getDisplayName(), p.getBestScore())));
        return entries;
    }

    public List<LeaderboardEntry> getRegionalLeaderboard(String region, int top) {
        SqlQuerySpec spec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore FROM c WHERE c.region = @region ORDER BY c.bestScore DESC OFFSET 0 LIMIT @top",
                Arrays.asList(new SqlParameter("@region", region), new SqlParameter("@top", top)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<LeaderboardEntry> entries = new ArrayList<>();
        int[] rank = {1};
        playersContainer.queryItems(spec, options, PlayerProjection.class)
                .forEach(p -> entries.add(new LeaderboardEntry(rank[0]++, p.getPlayerId(), p.getDisplayName(), p.getBestScore())));
        return entries;
    }

    public int countPlayersWithHigherScore(int score) {
        SqlQuerySpec spec = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score",
                Arrays.asList(new SqlParameter("@score", score)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return StreamSupport.stream(
                playersContainer.queryItems(spec, options, Integer.class).spliterator(), false)
                .findFirst()
                .orElse(0);
    }

    public List<LeaderboardEntry> getLeaderboardPage(int offset, int limit) {
        SqlQuerySpec spec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC OFFSET @offset LIMIT @limit",
                Arrays.asList(new SqlParameter("@offset", offset), new SqlParameter("@limit", limit)));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<PlayerProjection> projections = new ArrayList<>();
        playersContainer.queryItems(spec, options, PlayerProjection.class)
                .forEach(projections::add);

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < projections.size(); i++) {
            PlayerProjection p = projections.get(i);
            entries.add(new LeaderboardEntry(offset + i + 1, p.getPlayerId(), p.getDisplayName(), p.getBestScore()));
        }
        return entries;
    }

    // Internal projection class for leaderboard queries
    public static class PlayerProjection {
        private String playerId;
        private String displayName;
        private int bestScore;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public int getBestScore() { return bestScore; }
        public void setBestScore(int bestScore) { this.bestScore = bestScore; }
    }
}

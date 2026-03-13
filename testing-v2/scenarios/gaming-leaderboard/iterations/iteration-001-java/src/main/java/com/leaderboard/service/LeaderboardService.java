package com.leaderboard.service;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.leaderboard.dto.LeaderboardEntryResponse;
import com.leaderboard.dto.PlayerRankResponse;
import com.leaderboard.model.LeaderboardEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class LeaderboardService {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

    private static final String GLOBAL_KEY = "global";

    private final CosmosContainer leaderboardContainer;

    @Autowired
    public LeaderboardService(@Qualifier("leaderboardContainer") CosmosContainer leaderboardContainer) {
        this.leaderboardContainer = leaderboardContainer;
    }

    /**
     * Upsert a player's entry in the global and regional leaderboards.
     * Called when a player achieves a new best score.
     * Materialized view pattern: single-partition writes, single-partition reads (Rule 9.1).
     */
    public void upsertLeaderboardEntries(String playerId, String displayName,
                                         int score, String region) {
        upsertEntry(GLOBAL_KEY, playerId, displayName, score, region);
        upsertEntry(region, playerId, displayName, score, region);
    }

    private void upsertEntry(String leaderboardKey, String playerId,
                              String displayName, int score, String region) {
        LeaderboardEntry entry = new LeaderboardEntry(
                leaderboardKey, playerId, displayName, score, region);
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        leaderboardContainer.upsertItem(entry, new PartitionKey(leaderboardKey), options);
        logger.info("Upserted leaderboard entry: key={} player={} score={}",
                leaderboardKey, playerId, score);
    }

    /**
     * Get the global top N leaderboard sorted by score descending.
     * All entries share partition key "global" — single-partition query, no fan-out (Rule 9.1).
     * Uses literal N in OFFSET/LIMIT to satisfy best practices (Rule 3.6).
     * Validated top to 100 max before calling.
     */
    public List<LeaderboardEntryResponse> getGlobalLeaderboard(int top) {
        return getLeaderboard(GLOBAL_KEY, top);
    }

    /**
     * Get the regional top N leaderboard for the given region.
     * Single-partition query against the region partition key (Rule 9.1).
     */
    public List<LeaderboardEntryResponse> getRegionalLeaderboard(String region, int top) {
        return getLeaderboard(region, top);
    }

    private List<LeaderboardEntryResponse> getLeaderboard(String leaderboardKey, int top) {
        int limit = Math.min(top, 100);
        // Use parameterized query for leaderboardKey; embed limit as literal (Rule 3.6 / 3.5).
        // Query runs within a single partition — no cross-partition fan-out (Rule 3.1).
        // Project only needed fields to reduce payload (Rule 3.7).
        String sql = "SELECT c.playerId, c.displayName, c.score FROM c "
                + "WHERE c.leaderboardKey = @key "
                + "ORDER BY c.score DESC "
                + "OFFSET 0 LIMIT " + limit;

        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setPartitionKey(new PartitionKey(leaderboardKey));

        SqlQuerySpec querySpec = new SqlQuerySpec(sql,
                Arrays.asList(new SqlParameter("@key", leaderboardKey)));

        CosmosPagedIterable<LeaderboardEntry> iterable = leaderboardContainer.queryItems(
                querySpec, queryOptions, LeaderboardEntry.class);

        List<LeaderboardEntryResponse> result = new ArrayList<>();
        int rank = 1;
        for (LeaderboardEntry entry : iterable) {
            result.add(new LeaderboardEntryResponse(
                    rank++, entry.getPlayerId(), entry.getDisplayName(), entry.getScore()));
        }
        return result;
    }

    /**
     * Get a player's rank and ±10 neighboring players on the global leaderboard.
     *
     * Rank is computed with a COUNT query — O(1) in RU cost regardless of leaderboard
     * size — avoiding full partition scans (Rule 9.2).
     *
     * Neighbors are found via two score-range queries:
     *   - 10 players immediately above (score > player's score, ORDER BY score ASC LIMIT 10)
     *   - 10 players immediately below (score < player's score, ORDER BY score DESC LIMIT 10)
     */
    public PlayerRankResponse getPlayerRank(String playerId) {
        LeaderboardEntry playerEntry = getPlayerEntry(playerId);
        if (playerEntry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Player not found or has no scores: " + playerId);
        }

        int playerScore = playerEntry.getScore();

        // Count-based rank: number of players with higher score + 1 (Rule 9.2)
        int rank = countPlayersAbove(GLOBAL_KEY, playerScore) + 1;

        // Neighbors above: score > playerScore, ORDER BY score ASC LIMIT 10
        // After fetching, reverse to get descending rank order
        List<LeaderboardEntry> above = getNeighborsAbove(GLOBAL_KEY, playerScore, 10);

        // Neighbors below: score < playerScore, ORDER BY score DESC LIMIT 10
        List<LeaderboardEntry> below = getNeighborsBelow(GLOBAL_KEY, playerScore, 10);

        List<LeaderboardEntryResponse> neighbors = new ArrayList<>();

        // Above neighbors: sorted ASC, so last entry is rank R-1.
        // Reverse and assign descending ranks starting from R-1.
        for (int i = above.size() - 1; i >= 0; i--) {
            int neighborRank = rank - (above.size() - i);
            if (neighborRank >= 1) {
                LeaderboardEntry e = above.get(i);
                neighbors.add(new LeaderboardEntryResponse(
                        neighborRank, e.getPlayerId(), e.getDisplayName(), e.getScore()));
            }
        }

        // Below neighbors: already in descending score order, assign ranks R+1, R+2, ...
        for (int i = 0; i < below.size(); i++) {
            LeaderboardEntry e = below.get(i);
            neighbors.add(new LeaderboardEntryResponse(
                    rank + i + 1, e.getPlayerId(), e.getDisplayName(), e.getScore()));
        }

        return new PlayerRankResponse(playerId, rank, playerScore, neighbors);
    }

    private LeaderboardEntry getPlayerEntry(String playerId) {
        try {
            CosmosItemResponse<LeaderboardEntry> response = leaderboardContainer.readItem(
                    playerId, new PartitionKey(GLOBAL_KEY), LeaderboardEntry.class);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Count players with a score strictly greater than the given score.
     * Uses parameterized query for safety (Rule 3.5).
     * Single-partition query within the global leaderboard partition (Rule 3.1).
     */
    private int countPlayersAbove(String leaderboardKey, int score) {
        String sql = "SELECT VALUE COUNT(1) FROM c "
                + "WHERE c.leaderboardKey = @key AND c.score > @score";
        CosmosQueryRequestOptions opts = new CosmosQueryRequestOptions();
        opts.setPartitionKey(new PartitionKey(leaderboardKey));

        SqlQuerySpec querySpec = new SqlQuerySpec(sql,
                Arrays.asList(
                        new SqlParameter("@key", leaderboardKey),
                        new SqlParameter("@score", score)));

        CosmosPagedIterable<Integer> iterable = leaderboardContainer.queryItems(
                querySpec, opts, Integer.class);

        for (Integer count : iterable) {
            if (count != null) return count;
        }
        return 0;
    }

    private List<LeaderboardEntry> getNeighborsAbove(String leaderboardKey, int score, int limit) {
        String sql = "SELECT c.playerId, c.displayName, c.score FROM c "
                + "WHERE c.leaderboardKey = @key AND c.score > @score "
                + "ORDER BY c.score ASC "
                + "OFFSET 0 LIMIT " + limit;
        return queryEntries(leaderboardKey, sql, score);
    }

    private List<LeaderboardEntry> getNeighborsBelow(String leaderboardKey, int score, int limit) {
        String sql = "SELECT c.playerId, c.displayName, c.score FROM c "
                + "WHERE c.leaderboardKey = @key AND c.score < @score "
                + "ORDER BY c.score DESC "
                + "OFFSET 0 LIMIT " + limit;
        return queryEntries(leaderboardKey, sql, score);
    }

    private List<LeaderboardEntry> queryEntries(String leaderboardKey, String sql, int score) {
        CosmosQueryRequestOptions opts = new CosmosQueryRequestOptions();
        opts.setPartitionKey(new PartitionKey(leaderboardKey));

        SqlQuerySpec querySpec = new SqlQuerySpec(sql,
                Arrays.asList(
                        new SqlParameter("@key", leaderboardKey),
                        new SqlParameter("@score", score)));

        CosmosPagedIterable<LeaderboardEntry> iterable = leaderboardContainer.queryItems(
                querySpec, opts, LeaderboardEntry.class);

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (LeaderboardEntry e : iterable) {
            entries.add(e);
        }
        return entries;
    }
}

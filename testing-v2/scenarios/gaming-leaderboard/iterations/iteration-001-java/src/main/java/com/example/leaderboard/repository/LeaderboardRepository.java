package com.example.leaderboard.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.leaderboard.model.LeaderboardEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for materialized leaderboard entries.
 *
 * Rule 9.1: Leaderboard is a materialized view updated on score submission.
 *           Partition key = leaderboardKey ("global" or region), so all top-N
 *           queries are single-partition — fast and cheap.
 *
 * Rule 9.2: COUNT-based rank query instead of full partition scan.
 * Rule 3.6: Literal integers for TOP / OFFSET LIMIT — not query parameters.
 * Rule 3.5: Parameterized queries for user-supplied values (score).
 */
@Repository
public class LeaderboardRepository {

    private final CosmosContainer container;

    public LeaderboardRepository(@Qualifier("leaderboardContainer") CosmosContainer container) {
        this.container = container;
    }

    // Rule 4.9: contentResponseOnWriteEnabled set at client level
    public void upsert(LeaderboardEntry entry) {
        container.upsertItem(
            entry,
            new PartitionKey(entry.getLeaderboardKey()),
            new CosmosItemRequestOptions());
    }

    public Optional<LeaderboardEntry> findByPlayerIdInPartition(String leaderboardKey, String playerId) {
        try {
            CosmosItemResponse<LeaderboardEntry> response = container.readItem(
                playerId, new PartitionKey(leaderboardKey), LeaderboardEntry.class);
            return Optional.ofNullable(response.getItem());
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Returns the top N entries from a leaderboard partition sorted by score descending.
     * Rule 3.6: topN is embedded as a literal integer — Cosmos DB does not support
     *           parameterized TOP values.
     * Rule 3.1: Query scoped to single partition via QueryRequestOptions.
     */
    public List<LeaderboardEntry> getTopN(String leaderboardKey, int topN) {
        // Validate to prevent SQL injection before embedding as literal
        if (topN < 1 || topN > 1000) {
            topN = 100;
        }
        // Rule 3.6: literal integer for TOP
        String sql = "SELECT TOP " + topN +
                     " c.id, c.playerId, c.displayName, c.score, c.leaderboardKey" +
                     " FROM c ORDER BY c.score DESC";

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        CosmosPagedIterable<LeaderboardEntry> results =
            container.queryItems(sql, options, LeaderboardEntry.class);

        List<LeaderboardEntry> entries = new ArrayList<>();
        results.forEach(entries::add);
        return entries;
    }

    /**
     * Counts players with a score strictly higher than the given score.
     * rank = count + 1
     *
     * Rule 9.2: COUNT-based rank — O(1) regardless of leaderboard size.
     * Rule 3.5: Parameterized query for the score value.
     * Rule 3.1: Single-partition query.
     */
    public int countPlayersAbove(String leaderboardKey, int playerScore) {
        String sql = "SELECT VALUE COUNT(1) FROM c WHERE c.score > @score";

        SqlQuerySpec query = new SqlQuerySpec(sql,
            new SqlParameter("@score", playerScore));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        CosmosPagedIterable<Integer> results =
            container.queryItems(query, options, Integer.class);

        List<Integer> counts = new ArrayList<>();
        results.forEach(counts::add);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    /**
     * Returns players surrounding rank R using OFFSET LIMIT.
     * Rule 3.6: Offset and limit values are literal integers in the SQL string.
     * Rule 3.1: Single-partition query.
     */
    public List<LeaderboardEntry> getNeighbors(String leaderboardKey, int rank, int range) {
        int offset = Math.max(0, rank - 1 - range);
        // Fetch range*2+2 entries — one extra to compensate for the current player
        // being in the result set. After filtering the player, we retain up to range*2.
        int limit = range * 2 + 2;

        // Rule 3.6: literal integers for OFFSET and LIMIT
        String sql = "SELECT c.id, c.playerId, c.displayName, c.score, c.leaderboardKey" +
                     " FROM c ORDER BY c.score DESC" +
                     " OFFSET " + offset + " LIMIT " + limit;

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        CosmosPagedIterable<LeaderboardEntry> results =
            container.queryItems(sql, options, LeaderboardEntry.class);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int[] idx = {0};
        results.forEach(entry -> {
            // Compute rank based on position in sorted result
            entry.setRankPosition(offset + idx[0] + 1);
            idx[0]++;
            entries.add(entry);
        });
        return entries;
    }
}

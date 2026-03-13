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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class LeaderboardService {

    private final CosmosContainer leaderboardContainer;

    public LeaderboardService(@Qualifier("leaderboardContainer") CosmosContainer leaderboardContainer) {
        this.leaderboardContainer = leaderboardContainer;
    }

    /**
     * Upsert a player's entry in the given leaderboard partition.
     * Called after every score submission to keep the materialized view current.
     */
    public void upsertLeaderboardEntry(String leaderboardKey, String playerId,
                                       String displayName, int bestScore) {
        LeaderboardEntry entry = new LeaderboardEntry(leaderboardKey, playerId, displayName, bestScore);
        try {
            leaderboardContainer.upsertItem(
                    entry,
                    new PartitionKey(leaderboardKey),
                    new CosmosItemRequestOptions());
        } catch (CosmosException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update leaderboard: " + e.getMessage());
        }
    }

    /**
     * Return the top N entries for a given leaderboard, sorted by bestScore descending.
     * This is a single-partition query (efficient).
     * Uses OFFSET/LIMIT rather than TOP @param to comply with SDK parameterization rules.
     */
    public List<LeaderboardEntry> getTopN(String leaderboardKey, int top) {
        // Project only needed fields (rule 3.7)
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore, c.leaderboardKey, c.id " +
                "FROM c WHERE c.leaderboardKey = @leaderboardKey " +
                "ORDER BY c.bestScore DESC " +
                "OFFSET 0 LIMIT @top",
                Arrays.asList(
                        new SqlParameter("@leaderboardKey", leaderboardKey),
                        new SqlParameter("@top", top)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        List<LeaderboardEntry> results = new ArrayList<>();
        CosmosPagedIterable<LeaderboardEntry> iterable =
                leaderboardContainer.queryItems(querySpec, options, LeaderboardEntry.class);
        iterable.forEach(results::add);
        return results;
    }

    /**
     * Get the rank of a player in a given leaderboard using a count-based query.
     * Rank = (count of players with bestScore strictly greater than player's bestScore) + 1.
     * This avoids a full partition scan; a single aggregation query is enough.
     */
    public int getPlayerRank(String leaderboardKey, int playerScore) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c " +
                "WHERE c.leaderboardKey = @leaderboardKey AND c.bestScore > @score",
                Arrays.asList(
                        new SqlParameter("@leaderboardKey", leaderboardKey),
                        new SqlParameter("@score", playerScore)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        List<Integer> countResult = new ArrayList<>();
        leaderboardContainer.queryItems(querySpec, options, Integer.class)
                .forEach(countResult::add);

        int countAbove = countResult.isEmpty() ? 0 : countResult.get(0);
        return countAbove + 1;
    }

    /**
     * Retrieve the surrounding players (±10 positions) for a given rank.
     * Uses OFFSET/LIMIT to page to the right window efficiently.
     */
    public List<LeaderboardEntry> getNeighbors(String leaderboardKey, int rank, int windowSize) {
        int offset = Math.max(0, rank - windowSize - 1);
        int limit = windowSize * 2 + 1; // include players above and below

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.bestScore, c.leaderboardKey, c.id " +
                "FROM c WHERE c.leaderboardKey = @leaderboardKey " +
                "ORDER BY c.bestScore DESC " +
                "OFFSET @offset LIMIT @limit",
                Arrays.asList(
                        new SqlParameter("@leaderboardKey", leaderboardKey),
                        new SqlParameter("@offset", offset),
                        new SqlParameter("@limit", limit)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        List<LeaderboardEntry> results = new ArrayList<>();
        leaderboardContainer.queryItems(querySpec, options, LeaderboardEntry.class)
                .forEach(results::add);
        return results;
    }

    /**
     * Check whether a player has an entry in the global leaderboard (has submitted at least one score).
     */
    public boolean hasLeaderboardEntry(String leaderboardKey, String playerId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c " +
                "WHERE c.leaderboardKey = @leaderboardKey AND c.playerId = @playerId",
                Arrays.asList(
                        new SqlParameter("@leaderboardKey", leaderboardKey),
                        new SqlParameter("@playerId", playerId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(leaderboardKey));

        List<Integer> countResult = new ArrayList<>();
        leaderboardContainer.queryItems(querySpec, options, Integer.class)
                .forEach(countResult::add);

        return !countResult.isEmpty() && countResult.get(0) > 0;
    }
}

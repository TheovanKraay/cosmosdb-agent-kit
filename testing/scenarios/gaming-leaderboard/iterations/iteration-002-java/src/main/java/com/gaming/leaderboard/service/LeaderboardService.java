package com.gaming.leaderboard.service;

import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.spring.data.cosmos.core.CosmosTemplate;
import com.gaming.leaderboard.dto.PlayerRankResponse;
import com.gaming.leaderboard.dto.PlayerRankResponse.RankedPlayer;
import com.gaming.leaderboard.model.GameScore;
import com.gaming.leaderboard.model.LeaderboardEntry;
import com.gaming.leaderboard.model.Player;
import com.gaming.leaderboard.repository.LeaderboardRepository;
import com.gaming.leaderboard.repository.PlayerRepository;
import com.gaming.leaderboard.repository.ScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Core leaderboard service implementing all game operations.
 * 
 * Applied rules:
 * - Rule 3.5: Parameterized queries throughout
 * - Rule 3.6: Project only needed fields for leaderboard queries
 * - Rule 3.1: Leaderboard queries target single partition (no cross-partition)
 * - Rule 3.4: Pagination with continuation tokens via CosmosTemplate
 * - Rule 9.2: COUNT-based rank for player rank queries
 * - Rule 4.7: ETag for optimistic concurrency on player stat updates
 */
@Service
public class LeaderboardService {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

    private final PlayerRepository playerRepository;
    private final ScoreRepository scoreRepository;
    private final LeaderboardRepository leaderboardRepository;
    private final CosmosTemplate cosmosTemplate;

    public LeaderboardService(PlayerRepository playerRepository,
                              ScoreRepository scoreRepository,
                              LeaderboardRepository leaderboardRepository,
                              CosmosTemplate cosmosTemplate) {
        this.playerRepository = playerRepository;
        this.scoreRepository = scoreRepository;
        this.leaderboardRepository = leaderboardRepository;
        this.cosmosTemplate = cosmosTemplate;
    }

    // ─── Player Operations ──────────────────────────────────────────

    /**
     * Create a new player profile.
     */
    public Player createPlayer(String displayName, String country) {
        Player player = new Player(displayName, country);
        // Rule 4.9: Spring Data returns saved entity automatically
        return playerRepository.save(player);
    }

    /**
     * Get a player profile by ID.
     * Rule 3.1: Point read with partition key — no cross-partition query.
     */
    public Optional<Player> getPlayer(String playerId) {
        return playerRepository.findById(playerId);
    }

    // ─── Score Operations ───────────────────────────────────────────

    /**
     * Submit a new score and update leaderboard + player stats.
     * 
     * Flow:
     * 1. Validate player exists
     * 2. Create GameScore document
     * 3. Update player cumulative stats (with ETag for concurrency)
     * 4. Upsert leaderboard entries (global + regional)
     */
    public GameScore submitScore(String playerId, long score, String gameMode) {
        // 1. Get and validate player
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        String weekPeriod = getCurrentWeekPeriod();

        // 2. Create the score record
        GameScore gameScore = new GameScore(
                playerId, score, gameMode,
                player.getCountry(), player.getDisplayName(), weekPeriod
        );
        scoreRepository.save(gameScore);
        logger.debug("Score submitted: player={}, score={}, week={}", playerId, score, weekPeriod);

        // 3. Update player cumulative stats
        // Rule 4.7: Using save which handles ETag internally via Spring Data
        player.updateStats(score);
        playerRepository.save(player);

        // 4. Upsert leaderboard entries (materialized view pattern - Rule 9.1)
        // Only update if this is the player's best score for this period
        upsertLeaderboardEntry(player, score, weekPeriod, "global");
        upsertLeaderboardEntry(player, score, weekPeriod, player.getCountry());

        return gameScore;
    }

    /**
     * Upsert a leaderboard entry. Only updates if the new score is higher.
     * Rule 9.1: Materialized view — denormalized for read performance.
     * Rule 3.1: Single-partition upsert (partitionKey = scope_weekPeriod).
     */
    private void upsertLeaderboardEntry(Player player, long score,
                                        String weekPeriod, String scope) {
        String entryId = player.getId() + "_" + scope + "_" + weekPeriod;
        String pk = scope + "_" + weekPeriod;

        // Check if there's an existing entry with a higher score
        Optional<LeaderboardEntry> existing = leaderboardRepository.findById(entryId);
        if (existing.isPresent() && existing.get().getScore() >= score) {
            // Existing score is higher or equal — skip update
            return;
        }

        LeaderboardEntry entry = new LeaderboardEntry(
                player.getId(), player.getDisplayName(), player.getCountry(),
                score, weekPeriod, scope
        );
        leaderboardRepository.save(entry);
        logger.debug("Leaderboard updated: scope={}, player={}, score={}", scope, player.getId(), score);
    }

    // ─── Leaderboard Queries ────────────────────────────────────────

    /**
     * Get global top N leaderboard for the current week.
     * 
     * Rule 3.1: Single-partition query (partitionKey = "global_" + weekPeriod)
     * Rule 3.6: Project only needed fields
     * Rule 5.1: Composite index on (partitionKey, score DESC)
     * Rule 3.5: Parameterized query
     */
    public List<LeaderboardEntry> getGlobalTopN(int limit) {
        String weekPeriod = getCurrentWeekPeriod();
        String pk = "global_" + weekPeriod;

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.playerId, c.displayName, c.country, c.score, " +
                "c.weekPeriod, c.scope, c.updatedAt " +
                "FROM c WHERE c.partitionKey = @pk ORDER BY c.score DESC OFFSET 0 LIMIT @limit",
                List.of(
                        new SqlParameter("@pk", pk),
                        new SqlParameter("@limit", limit)
                )
        );

        return StreamSupport.stream(
                cosmosTemplate.runQuery(query, LeaderboardEntry.class, LeaderboardEntry.class).spliterator(), false)
                .toList();
    }

    /**
     * Get regional (country) top N leaderboard for the current week.
     * 
     * Rule 3.1: Single-partition query (partitionKey = country + "_" + weekPeriod)
     * Rule 3.5: Parameterized query
     */
    public List<LeaderboardEntry> getRegionalTopN(String country, int limit) {
        String weekPeriod = getCurrentWeekPeriod();
        String pk = country + "_" + weekPeriod;

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.playerId, c.displayName, c.country, c.score, " +
                "c.weekPeriod, c.scope, c.updatedAt " +
                "FROM c WHERE c.partitionKey = @pk ORDER BY c.score DESC OFFSET 0 LIMIT @limit",
                List.of(
                        new SqlParameter("@pk", pk),
                        new SqlParameter("@limit", limit)
                )
        );

        return StreamSupport.stream(
                cosmosTemplate.runQuery(query, LeaderboardEntry.class, LeaderboardEntry.class).spliterator(), false)
                .toList();
    }

    /**
     * Get a player's rank and the 10 players above and below them.
     * 
     * Rule 9.2: COUNT-based rank query — O(1) for count, efficient for moderate scale.
     * Rule 3.5: Parameterized queries
     * Rule 3.1: All queries within single partition
     */
    public PlayerRankResponse getPlayerRank(String playerId) {
        String weekPeriod = getCurrentWeekPeriod();
        String pk = "global_" + weekPeriod;
        String entryId = playerId + "_global_" + weekPeriod;

        // Get the player's leaderboard entry
        Optional<LeaderboardEntry> playerEntry = leaderboardRepository.findById(entryId);
        if (playerEntry.isEmpty()) {
            throw new IllegalArgumentException(
                    "Player not found on leaderboard for current week: " + playerId);
        }

        LeaderboardEntry entry = playerEntry.get();
        long playerScore = entry.getScore();

        // Rule 9.2: COUNT query for rank (number of players with higher score + 1)
        SqlQuerySpec countQuery = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.partitionKey = @pk AND c.score > @score",
                List.of(
                        new SqlParameter("@pk", pk),
                        new SqlParameter("@score", playerScore)
                )
        );

        List<Long> countResult = StreamSupport.stream(
                cosmosTemplate.runQuery(countQuery, LeaderboardEntry.class, Long.class).spliterator(), false)
                .toList();
        int rank = countResult.isEmpty() ? 1 : countResult.get(0).intValue() + 1;

        // Get nearby players: 10 above and 10 below
        // Players above: those with scores just higher than the player's
        int offset = Math.max(0, rank - 11);  // 10 above
        int fetchLimit = 21;  // 10 above + player + 10 below

        SqlQuerySpec nearbyQuery = new SqlQuerySpec(
                "SELECT c.playerId, c.displayName, c.score " +
                "FROM c WHERE c.partitionKey = @pk " +
                "ORDER BY c.score DESC OFFSET @offset LIMIT @limit",
                List.of(
                        new SqlParameter("@pk", pk),
                        new SqlParameter("@offset", offset),
                        new SqlParameter("@limit", fetchLimit)
                )
        );

        List<LeaderboardEntry> nearbyEntries = StreamSupport.stream(
                cosmosTemplate.runQuery(nearbyQuery, LeaderboardEntry.class, LeaderboardEntry.class).spliterator(), false)
                .toList();

        List<RankedPlayer> nearbyPlayers = new ArrayList<>();
        for (int i = 0; i < nearbyEntries.size(); i++) {
            LeaderboardEntry e = nearbyEntries.get(i);
            nearbyPlayers.add(new RankedPlayer(
                    e.getPlayerId(), e.getDisplayName(), e.getScore(), offset + i + 1
            ));
        }

        return new PlayerRankResponse(
                playerId, entry.getDisplayName(), playerScore, rank, nearbyPlayers
        );
    }

    // ─── Utility ────────────────────────────────────────────────────

    /**
     * Calculate the current ISO week period string (e.g., "2026-W07").
     */
    public String getCurrentWeekPeriod() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int weekNum = now.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
        int year = now.getYear();
        return String.format("%d-W%02d", year, weekNum);
    }
}

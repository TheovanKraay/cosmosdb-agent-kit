package com.example.leaderboard.service;

import com.example.leaderboard.dto.LeaderboardEntryResponse;
import com.example.leaderboard.dto.PlayerRankResponse;
import com.example.leaderboard.dto.ScoreResponse;
import com.example.leaderboard.dto.SubmitScoreRequest;
import com.example.leaderboard.exception.PlayerNotFoundException;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.Score;
import com.example.leaderboard.repository.LeaderboardRepository;
import com.example.leaderboard.repository.PlayerRepository;
import com.example.leaderboard.repository.ScoreRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScoreService {

    private final PlayerRepository playerRepository;
    private final ScoreRepository scoreRepository;
    private final LeaderboardRepository leaderboardRepository;

    public ScoreService(PlayerRepository playerRepository,
                        ScoreRepository scoreRepository,
                        LeaderboardRepository leaderboardRepository) {
        this.playerRepository = playerRepository;
        this.scoreRepository = scoreRepository;
        this.leaderboardRepository = leaderboardRepository;
    }

    /**
     * Submits a game score for a player.
     * Updates player stats and the materialized leaderboard entries.
     *
     * Rule 9.1: On score submission, update the leaderboard materialized view
     *           for "global" and region partitions when a new best score is achieved.
     */
    public ScoreResponse submitScore(SubmitScoreRequest request) {
        String playerId = request.getPlayerId();
        int newScore = request.getScore();

        // Retrieve player — 404 if not found
        Player player = playerRepository.findByPlayerId(playerId)
            .orElseThrow(() -> new PlayerNotFoundException(playerId));

        // Create score document
        Score score = new Score(
            UUID.randomUUID().toString(),
            playerId,
            newScore,
            request.getGameMode(),
            Instant.now().toString()
        );
        scoreRepository.save(score);

        // Update player stats
        int oldBestScore = player.getBestScore();
        int oldTotalGames = player.getTotalGames();

        player.setTotalGames(oldTotalGames + 1);
        player.setTotalScore(player.getTotalScore() + newScore);
        player.setBestScore(Math.max(oldBestScore, newScore));
        player.setAverageScore((double) player.getTotalScore() / player.getTotalGames());
        playerRepository.save(player);

        // Rule 9.1: Update materialized leaderboard when player gets a new best score
        //           or when this is their first score submission.
        boolean isFirstScore = (oldTotalGames == 0);
        boolean isNewBest = (newScore > oldBestScore);

        if (isFirstScore || isNewBest) {
            int leaderboardScore = player.getBestScore();
            leaderboardRepository.upsert(new LeaderboardEntry(
                "global", playerId, player.getDisplayName(), leaderboardScore));
            leaderboardRepository.upsert(new LeaderboardEntry(
                player.getRegion(), playerId, player.getDisplayName(), leaderboardScore));
        }

        return new ScoreResponse(score.getId(), playerId, newScore);
    }

    /**
     * Returns global top N leaderboard entries sorted by score descending.
     */
    public List<LeaderboardEntryResponse> getGlobalLeaderboard(int topN) {
        List<LeaderboardEntry> entries = leaderboardRepository.getTopN("global", topN);
        return assignRanks(entries);
    }

    /**
     * Returns regional top N leaderboard entries sorted by score descending.
     */
    public List<LeaderboardEntryResponse> getRegionalLeaderboard(String region, int topN) {
        List<LeaderboardEntry> entries = leaderboardRepository.getTopN(region, topN);
        return assignRanks(entries);
    }

    /**
     * Returns a player's global rank and surrounding players (±10 positions).
     *
     * Rule 9.2: COUNT-based rank query avoids full partition scan.
     */
    public PlayerRankResponse getPlayerRank(String playerId) {
        // Look up player's leaderboard entry — 404 if no scores submitted
        LeaderboardEntry playerEntry = leaderboardRepository
            .findByPlayerIdInPartition("global", playerId)
            .orElseThrow(() -> new PlayerNotFoundException(playerId));

        // Rule 9.2: rank = count(players with higher score) + 1
        int rank = leaderboardRepository.countPlayersAbove("global", playerEntry.getScore()) + 1;

        // Get neighbors in a single query using OFFSET LIMIT
        List<LeaderboardEntry> neighborEntries =
            leaderboardRepository.getNeighbors("global", rank, 10);

        List<LeaderboardEntryResponse> neighbors = new ArrayList<>();
        for (LeaderboardEntry entry : neighborEntries) {
            if (!entry.getPlayerId().equals(playerId)) {
                neighbors.add(new LeaderboardEntryResponse(
                    entry.getRankPosition(),
                    entry.getPlayerId(),
                    entry.getDisplayName(),
                    entry.getScore()
                ));
            }
        }

        return new PlayerRankResponse(playerId, rank, playerEntry.getScore(), neighbors);
    }

    // Assigns sequential 1-based ranks to a list sorted by score descending
    private List<LeaderboardEntryResponse> assignRanks(List<LeaderboardEntry> entries) {
        List<LeaderboardEntryResponse> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            result.add(new LeaderboardEntryResponse(i + 1, e.getPlayerId(), e.getDisplayName(), e.getScore()));
        }
        return result;
    }
}

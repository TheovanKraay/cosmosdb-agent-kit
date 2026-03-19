package com.gaming.leaderboard.controller;

import com.gaming.leaderboard.dto.ScoreSubmitRequest;
import com.gaming.leaderboard.model.GameScore;
import com.gaming.leaderboard.model.LeaderboardEntry;
import com.gaming.leaderboard.service.LeaderboardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Leaderboard and score REST API.
 */
@RestController
@RequestMapping("/api")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * POST /api/scores — Submit a new game score.
     */
    @PostMapping("/scores")
    public ResponseEntity<GameScore> submitScore(@Valid @RequestBody ScoreSubmitRequest request) {
        try {
            GameScore score = leaderboardService.submitScore(
                    request.getPlayerId(), request.getScore(), request.getGameMode());
            return ResponseEntity.status(HttpStatus.CREATED).body(score);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/leaderboard/global — Get global top 100 leaderboard.
     * Rule 3.1: Single-partition query, no cross-partition fan-out.
     */
    @GetMapping("/leaderboard/global")
    public ResponseEntity<List<LeaderboardEntry>> getGlobalLeaderboard(
            @RequestParam(defaultValue = "100") int limit) {
        List<LeaderboardEntry> entries = leaderboardService.getGlobalTopN(Math.min(limit, 100));
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/leaderboard/regional/{country} — Get regional top 100 leaderboard.
     * Rule 3.1: Single-partition query scoped to country.
     */
    @GetMapping("/leaderboard/regional/{country}")
    public ResponseEntity<List<LeaderboardEntry>> getRegionalLeaderboard(
            @PathVariable String country,
            @RequestParam(defaultValue = "100") int limit) {
        List<LeaderboardEntry> entries = leaderboardService.getRegionalTopN(country, Math.min(limit, 100));
        return ResponseEntity.ok(entries);
    }
}

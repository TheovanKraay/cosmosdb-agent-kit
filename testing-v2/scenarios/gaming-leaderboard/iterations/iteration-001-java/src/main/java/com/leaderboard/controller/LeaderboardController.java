package com.leaderboard.controller;

import com.leaderboard.dto.LeaderboardEntryResponse;
import com.leaderboard.dto.PlayerRankResponse;
import com.leaderboard.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @Autowired
    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * GET /api/leaderboards/global?top=N
     * Returns the global top N leaderboard (default 100, max 100), sorted by score desc.
     * Each entry has: rank (1-based), playerId, displayName, score.
     */
    @GetMapping("/leaderboards/global")
    public ResponseEntity<List<LeaderboardEntryResponse>> getGlobalLeaderboard(
            @RequestParam(value = "top", defaultValue = "100") int top) {
        List<LeaderboardEntryResponse> entries = leaderboardService.getGlobalLeaderboard(top);
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/leaderboards/regional/{region}?top=N
     * Returns the regional top N leaderboard for the specified region.
     */
    @GetMapping("/leaderboards/regional/{region}")
    public ResponseEntity<List<LeaderboardEntryResponse>> getRegionalLeaderboard(
            @PathVariable String region,
            @RequestParam(value = "top", defaultValue = "100") int top) {
        List<LeaderboardEntryResponse> entries = leaderboardService.getRegionalLeaderboard(region, top);
        return ResponseEntity.ok(entries);
    }

    /**
     * GET /api/players/{playerId}/rank
     * Returns the player's global rank, best score, and ±10 neighboring players.
     * Returns 404 if the player has no scores on the leaderboard.
     */
    @GetMapping("/players/{playerId}/rank")
    public ResponseEntity<PlayerRankResponse> getPlayerRank(
            @PathVariable String playerId) {
        PlayerRankResponse response = leaderboardService.getPlayerRank(playerId);
        return ResponseEntity.ok(response);
    }
}

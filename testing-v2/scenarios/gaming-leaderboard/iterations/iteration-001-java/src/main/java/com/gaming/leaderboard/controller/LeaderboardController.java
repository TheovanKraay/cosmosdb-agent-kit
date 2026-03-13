package com.gaming.leaderboard.controller;

import com.gaming.leaderboard.model.LeaderboardEntry;
import com.gaming.leaderboard.repository.PlayerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final PlayerRepository playerRepository;

    public LeaderboardController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @GetMapping("/global")
    public ResponseEntity<List<LeaderboardEntry>> getGlobalLeaderboard(
            @RequestParam(defaultValue = "100") int top) {
        int limit = Math.min(top, 100);
        List<LeaderboardEntry> entries = playerRepository.getGlobalLeaderboard(limit);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/regional/{region}")
    public ResponseEntity<List<LeaderboardEntry>> getRegionalLeaderboard(
            @PathVariable String region,
            @RequestParam(defaultValue = "100") int top) {
        int limit = Math.min(top, 100);
        List<LeaderboardEntry> entries = playerRepository.getRegionalLeaderboard(region, limit);
        return ResponseEntity.ok(entries);
    }
}

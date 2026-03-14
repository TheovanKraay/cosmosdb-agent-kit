package com.example.leaderboard.controller;

import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/global")
    public ResponseEntity<List<LeaderboardEntry>> getGlobalLeaderboard(
            @RequestParam(defaultValue = "100") int top) {
        List<LeaderboardEntry> leaderboard = leaderboardService.getGlobalLeaderboard(top);
        return ResponseEntity.ok(leaderboard);
    }

    @GetMapping("/regional/{region}")
    public ResponseEntity<List<LeaderboardEntry>> getRegionalLeaderboard(
            @PathVariable String region,
            @RequestParam(defaultValue = "100") int top) {
        List<LeaderboardEntry> leaderboard = leaderboardService.getRegionalLeaderboard(region, top);
        return ResponseEntity.ok(leaderboard);
    }
}

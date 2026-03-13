package com.example.leaderboard.controller;

import com.example.leaderboard.dto.LeaderboardEntryResponse;
import com.example.leaderboard.service.ScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final ScoreService scoreService;

    public LeaderboardController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/global")
    public ResponseEntity<List<LeaderboardEntryResponse>> getGlobalLeaderboard(
            @RequestParam(name = "top", defaultValue = "100") int top) {
        List<LeaderboardEntryResponse> leaderboard = scoreService.getGlobalLeaderboard(top);
        return ResponseEntity.ok(leaderboard);
    }

    @GetMapping("/regional/{region}")
    public ResponseEntity<List<LeaderboardEntryResponse>> getRegionalLeaderboard(
            @PathVariable String region,
            @RequestParam(name = "top", defaultValue = "100") int top) {
        List<LeaderboardEntryResponse> leaderboard = scoreService.getRegionalLeaderboard(region, top);
        return ResponseEntity.ok(leaderboard);
    }
}

package com.example.leaderboard.controller;

import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.repository.CosmosRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final CosmosRepository repository;

    @Autowired
    public LeaderboardController(CosmosRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/global")
    public ResponseEntity<List<LeaderboardEntry>> getGlobalLeaderboard(
            @RequestParam(defaultValue = "100") int top) {
        int limit = Math.min(top, 100);
        List<LeaderboardEntry> entries = repository.getGlobalLeaderboard(limit);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/regional/{region}")
    public ResponseEntity<List<LeaderboardEntry>> getRegionalLeaderboard(
            @PathVariable String region,
            @RequestParam(defaultValue = "100") int top) {
        int limit = Math.min(top, 100);
        List<LeaderboardEntry> entries = repository.getRegionalLeaderboard(region, limit);
        return ResponseEntity.ok(entries);
    }
}

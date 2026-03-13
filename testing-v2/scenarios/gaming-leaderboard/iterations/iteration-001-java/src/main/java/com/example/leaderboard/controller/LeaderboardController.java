package com.example.leaderboard.controller;

import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.service.LeaderboardService;
import com.example.leaderboard.service.ScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * GET /api/leaderboards/global?top=N
     * Returns the global top N leaderboard sorted by best score descending.
     * Default top = 100.
     */
    @GetMapping("/global")
    public ResponseEntity<List<Map<String, Object>>> getGlobalLeaderboard(
            @RequestParam(defaultValue = "100") int top) {
        String leaderboardKey = ScoreService.globalLeaderboardKey();
        List<LeaderboardEntry> entries = leaderboardService.getTopN(leaderboardKey, top);
        return ResponseEntity.ok(toRankedList(entries));
    }

    /**
     * GET /api/leaderboards/regional/{region}?top=N
     * Returns the top N leaderboard for the given region.
     * Default top = 100.
     */
    @GetMapping("/regional/{region}")
    public ResponseEntity<List<Map<String, Object>>> getRegionalLeaderboard(
            @PathVariable String region,
            @RequestParam(defaultValue = "100") int top) {
        String leaderboardKey = ScoreService.regionalLeaderboardKey(region);
        List<LeaderboardEntry> entries = leaderboardService.getTopN(leaderboardKey, top);
        return ResponseEntity.ok(toRankedList(entries));
    }

    /**
     * Convert a sorted list of leaderboard entries into the API response format,
     * assigning 1-based rank positions.
     */
    private List<Map<String, Object>> toRankedList(List<LeaderboardEntry> entries) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            result.add(Map.of(
                    "rank", i + 1,
                    "playerId", entry.getPlayerId(),
                    "displayName", entry.getDisplayName(),
                    "score", entry.getBestScore()));
        }
        return result;
    }
}

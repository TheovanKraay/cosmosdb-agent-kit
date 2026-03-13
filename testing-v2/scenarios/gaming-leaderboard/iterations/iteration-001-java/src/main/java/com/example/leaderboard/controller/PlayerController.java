package com.example.leaderboard.controller;

import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.service.LeaderboardService;
import com.example.leaderboard.service.PlayerService;
import com.example.leaderboard.service.ScoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;
    private final LeaderboardService leaderboardService;

    public PlayerController(PlayerService playerService, LeaderboardService leaderboardService) {
        this.playerService = playerService;
        this.leaderboardService = leaderboardService;
    }

    /**
     * POST /api/players
     * Create a new player profile.
     */
    @PostMapping
    public ResponseEntity<Player> createPlayer(@RequestBody Map<String, String> body) {
        String playerId = body.get("playerId");
        String displayName = body.get("displayName");
        String region = body.get("region");

        if (playerId == null || displayName == null || region == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "playerId, displayName, and region are required");
        }

        Player created = playerService.createPlayer(playerId, displayName, region);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/players/{playerId}
     * Get a player's profile with stats.
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable String playerId) {
        Player player = playerService.getPlayer(playerId);
        return ResponseEntity.ok(player);
    }

    /**
     * GET /api/players/{playerId}/rank
     * Return the player's rank on the global leaderboard plus ±10 neighbors.
     */
    @GetMapping("/{playerId}/rank")
    public ResponseEntity<Map<String, Object>> getPlayerRank(@PathVariable String playerId) {
        Player player = playerService.getPlayer(playerId); // 404 if not found

        // Return 404 if the player has never submitted a score
        if (player.getTotalGames() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Player has no scores: " + playerId);
        }

        String globalKey = ScoreService.globalLeaderboardKey();
        int rank = leaderboardService.getPlayerRank(globalKey, player.getBestScore());

        // Retrieve ±10 neighbors from the global leaderboard
        List<LeaderboardEntry> window = leaderboardService.getNeighbors(globalKey, rank, 10);

        // Build neighbor list, excluding the player themselves
        List<Map<String, Object>> neighbors = new ArrayList<>();
        for (int i = 0; i < window.size(); i++) {
            LeaderboardEntry entry = window.get(i);
            if (!entry.getPlayerId().equals(playerId)) {
                // Compute absolute rank for each neighbor based on window offset
                int offset = Math.max(0, rank - 11); // 0-based offset used in query
                int neighborRank = offset + i + 1;
                neighbors.add(Map.of(
                        "rank", neighborRank,
                        "playerId", entry.getPlayerId(),
                        "displayName", entry.getDisplayName(),
                        "score", entry.getBestScore()));
            }
        }

        return ResponseEntity.ok(Map.of(
                "playerId", player.getPlayerId(),
                "rank", rank,
                "score", player.getBestScore(),
                "neighbors", neighbors));
    }
}

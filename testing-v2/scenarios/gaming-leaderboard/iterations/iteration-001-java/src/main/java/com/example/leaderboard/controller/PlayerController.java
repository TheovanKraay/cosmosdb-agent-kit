package com.example.leaderboard.controller;

import com.azure.cosmos.CosmosException;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.PlayerRankResponse;
import com.example.leaderboard.service.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final LeaderboardService leaderboardService;

    public PlayerController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @PostMapping
    public ResponseEntity<?> createPlayer(@RequestBody Map<String, String> body) {
        String playerId = body.get("playerId");
        String displayName = body.get("displayName");
        String region = body.get("region");

        if (playerId == null || displayName == null || region == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "playerId, displayName, and region are required"));
        }

        try {
            Player player = leaderboardService.createPlayer(playerId, displayName, region);
            return ResponseEntity.status(HttpStatus.CREATED).body(player);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Player already exists"));
            }
            throw e;
        }
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<?> getPlayer(@PathVariable String playerId) {
        Optional<Player> player = leaderboardService.getPlayer(playerId);
        if (player.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Player not found"));
        }
        return ResponseEntity.ok(player.get());
    }

    @GetMapping("/{playerId}/rank")
    public ResponseEntity<?> getPlayerRank(@PathVariable String playerId) {
        Optional<PlayerRankResponse> rankResponse = leaderboardService.getPlayerRank(playerId);
        if (rankResponse.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Player not found or has no scores"));
        }
        return ResponseEntity.ok(rankResponse.get());
    }
}

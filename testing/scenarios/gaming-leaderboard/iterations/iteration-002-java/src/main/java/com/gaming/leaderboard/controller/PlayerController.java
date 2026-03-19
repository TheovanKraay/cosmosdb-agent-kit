package com.gaming.leaderboard.controller;

import com.gaming.leaderboard.dto.PlayerCreateRequest;
import com.gaming.leaderboard.dto.PlayerRankResponse;
import com.gaming.leaderboard.model.Player;
import com.gaming.leaderboard.service.LeaderboardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Player profile REST API.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final LeaderboardService leaderboardService;

    public PlayerController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * POST /api/players — Create a new player profile.
     */
    @PostMapping
    public ResponseEntity<Player> createPlayer(@Valid @RequestBody PlayerCreateRequest request) {
        Player player = leaderboardService.createPlayer(request.getDisplayName(), request.getCountry());
        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }

    /**
     * GET /api/players/{playerId} — Get player profile with stats.
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable String playerId) {
        Optional<Player> player = leaderboardService.getPlayer(playerId);
        return player.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/players/{playerId}/rank — Get player's rank and nearby players.
     */
    @GetMapping("/{playerId}/rank")
    public ResponseEntity<PlayerRankResponse> getPlayerRank(@PathVariable String playerId) {
        try {
            PlayerRankResponse response = leaderboardService.getPlayerRank(playerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

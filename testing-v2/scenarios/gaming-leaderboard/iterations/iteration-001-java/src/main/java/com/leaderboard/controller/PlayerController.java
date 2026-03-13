package com.leaderboard.controller;

import com.leaderboard.dto.CreatePlayerRequest;
import com.leaderboard.dto.PlayerResponse;
import com.leaderboard.model.Player;
import com.leaderboard.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    @Autowired
    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /**
     * POST /api/players
     * Create a new player profile. Returns 201 with the created player.
     * New players start with totalGames=0, bestScore=0, averageScore=0.
     */
    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(
            @RequestBody CreatePlayerRequest request) {
        Player player = playerService.createPlayer(
                request.getPlayerId(), request.getDisplayName(), request.getRegion());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PlayerResponse(player));
    }

    /**
     * GET /api/players/{playerId}
     * Get a player's profile with cumulative stats. Returns 404 if not found.
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponse> getPlayer(@PathVariable String playerId) {
        Player player = playerService.getPlayer(playerId);
        return ResponseEntity.ok(new PlayerResponse(player));
    }
}

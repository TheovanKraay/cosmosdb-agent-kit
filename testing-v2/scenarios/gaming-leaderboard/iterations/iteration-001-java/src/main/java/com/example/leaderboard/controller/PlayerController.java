package com.example.leaderboard.controller;

import com.example.leaderboard.dto.CreatePlayerRequest;
import com.example.leaderboard.dto.PlayerRankResponse;
import com.example.leaderboard.dto.PlayerResponse;
import com.example.leaderboard.service.PlayerService;
import com.example.leaderboard.service.ScoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;
    private final ScoreService scoreService;

    public PlayerController(PlayerService playerService, ScoreService scoreService) {
        this.playerService = playerService;
        this.scoreService = scoreService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(@RequestBody CreatePlayerRequest request) {
        PlayerResponse response = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponse> getPlayer(@PathVariable String playerId) {
        PlayerResponse response = playerService.getPlayer(playerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{playerId}/rank")
    public ResponseEntity<PlayerRankResponse> getPlayerRank(@PathVariable String playerId) {
        PlayerRankResponse response = scoreService.getPlayerRank(playerId);
        return ResponseEntity.ok(response);
    }
}

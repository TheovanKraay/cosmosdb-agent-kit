package com.gaming.leaderboard.controller;

import com.gaming.leaderboard.model.LeaderboardEntry;
import com.gaming.leaderboard.model.Player;
import com.gaming.leaderboard.model.PlayerRankResponse;
import com.gaming.leaderboard.repository.PlayerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerRepository playerRepository;

    public PlayerController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @PostMapping
    public ResponseEntity<Player> createPlayer(@RequestBody Map<String, String> body) {
        String playerId = body.get("playerId");
        String displayName = body.get("displayName");
        String region = body.get("region");

        if (playerId == null || displayName == null || region == null) {
            return ResponseEntity.badRequest().build();
        }

        Player player = new Player(playerId, displayName, region);
        Player created = playerRepository.createPlayer(player);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable String playerId) {
        Player player = playerRepository.getPlayer(playerId);
        if (player == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(player);
    }

    @GetMapping("/{playerId}/rank")
    public ResponseEntity<PlayerRankResponse> getPlayerRank(@PathVariable String playerId) {
        Player player = playerRepository.getPlayer(playerId);
        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        // rank = number of players with a higher bestScore + 1
        int rank = playerRepository.countPlayersWithHigherScore(player.getBestScore()) + 1;

        // fetch up to 10 players above (offset = rank-11, limited to 0) and 10 below
        int aboveOffset = Math.max(0, rank - 11);
        int windowSize = 21;
        List<LeaderboardEntry> window = playerRepository.getLeaderboardPage(aboveOffset, windowSize);

        // remove the player themselves from the neighbors list
        List<LeaderboardEntry> neighbors = window.stream()
                .filter(e -> !e.getPlayerId().equals(playerId))
                .toList();

        return ResponseEntity.ok(new PlayerRankResponse(playerId, rank, player.getBestScore(), neighbors));
    }
}

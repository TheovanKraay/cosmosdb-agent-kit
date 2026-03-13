package com.example.leaderboard.controller;

import com.example.leaderboard.model.CreatePlayerRequest;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.model.PlayerRankResponse;
import com.example.leaderboard.model.PlayerResponse;
import com.example.leaderboard.repository.CosmosRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final CosmosRepository repository;

    @Autowired
    public PlayerController(CosmosRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(@RequestBody CreatePlayerRequest request) {
        Player player = new Player();
        player.setPlayerId(request.getPlayerId());
        player.setDisplayName(request.getDisplayName());
        player.setRegion(request.getRegion());

        Player created = repository.createPlayer(player);
        return ResponseEntity.status(HttpStatus.CREATED).body(PlayerResponse.fromPlayer(created));
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponse> getPlayer(@PathVariable String playerId) {
        Optional<Player> playerOpt = repository.getPlayer(playerId);
        return playerOpt
                .map(p -> ResponseEntity.ok(PlayerResponse.fromPlayer(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{playerId}/rank")
    public ResponseEntity<PlayerRankResponse> getPlayerRank(@PathVariable String playerId) {
        Optional<PlayerRankResponse> rankOpt = repository.getPlayerRank(playerId);
        return rankOpt
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

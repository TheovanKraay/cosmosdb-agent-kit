package com.gaming.leaderboard.controller;

import com.gaming.leaderboard.model.Player;
import com.gaming.leaderboard.model.Score;
import com.gaming.leaderboard.repository.PlayerRepository;
import com.gaming.leaderboard.repository.ScoreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreRepository scoreRepository;
    private final PlayerRepository playerRepository;

    public ScoreController(ScoreRepository scoreRepository, PlayerRepository playerRepository) {
        this.scoreRepository = scoreRepository;
        this.playerRepository = playerRepository;
    }

    @PostMapping
    public ResponseEntity<Score> submitScore(@RequestBody Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        Object scoreObj = body.get("score");
        String gameMode = (String) body.get("gameMode");

        if (playerId == null || scoreObj == null) {
            return ResponseEntity.badRequest().build();
        }

        int scoreValue;
        try {
            scoreValue = ((Number) scoreObj).intValue();
        } catch (ClassCastException e) {
            return ResponseEntity.badRequest().build();
        }

        // Verify player exists
        Player player = playerRepository.getPlayer(playerId);
        if (player == null) {
            return ResponseEntity.status(404).build();
        }

        // Create score record
        String scoreId = UUID.randomUUID().toString();
        Score score = new Score(scoreId, playerId, scoreValue, gameMode);
        scoreRepository.createScore(score);

        // Update player stats
        int newTotalGames = player.getTotalGames() + 1;
        double newTotalScore = player.getTotalScore() + scoreValue;
        int newBestScore = Math.max(player.getBestScore(), scoreValue);
        double newAverageScore = newTotalScore / newTotalGames;

        player.setTotalGames(newTotalGames);
        player.setBestScore(newBestScore);
        player.setAverageScore(newAverageScore);
        player.setTotalScore(newTotalScore);
        playerRepository.updatePlayer(player);

        return ResponseEntity.status(201).body(score);
    }
}

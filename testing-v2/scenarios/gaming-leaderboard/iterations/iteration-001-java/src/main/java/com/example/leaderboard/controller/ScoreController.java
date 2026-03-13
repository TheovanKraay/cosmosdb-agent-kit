package com.example.leaderboard.controller;

import com.example.leaderboard.model.Score;
import com.example.leaderboard.service.ScoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * POST /api/scores
     * Submit a new game score for a player.
     * Updates player stats and both global + regional leaderboard materialized views.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitScore(@RequestBody Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        Object scoreObj = body.get("score");
        String gameMode = (String) body.get("gameMode"); // optional

        if (playerId == null || scoreObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "playerId and score are required");
        }

        int scoreValue;
        try {
            scoreValue = ((Number) scoreObj).intValue();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be an integer");
        }

        Score created = scoreService.submitScore(playerId, scoreValue, gameMode);

        // Return only the contract-required fields
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "scoreId", created.getScoreId(),
                "playerId", created.getPlayerId(),
                "score", created.getScore()));
    }
}

package com.example.leaderboard.controller;

import com.example.leaderboard.model.ScoreRecord;
import com.example.leaderboard.service.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final LeaderboardService leaderboardService;

    public ScoreController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @PostMapping
    public ResponseEntity<?> submitScore(@RequestBody Map<String, Object> body) {
        String playerId = (String) body.get("playerId");
        Object scoreObj = body.get("score");
        String gameMode = (String) body.get("gameMode");

        if (playerId == null || scoreObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "playerId and score are required"));
        }

        int score;
        try {
            score = ((Number) scoreObj).intValue();
        } catch (ClassCastException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "score must be a number"));
        }

        ScoreRecord record = leaderboardService.submitScore(playerId, score, gameMode);
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }
}

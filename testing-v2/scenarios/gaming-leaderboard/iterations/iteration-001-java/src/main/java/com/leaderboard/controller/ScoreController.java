package com.leaderboard.controller;

import com.leaderboard.dto.ScoreResponse;
import com.leaderboard.dto.SubmitScoreRequest;
import com.leaderboard.service.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService scoreService;

    @Autowired
    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * POST /api/scores
     * Submit a game score for a player. Returns 201 with scoreId, playerId, and score.
     */
    @PostMapping
    public ResponseEntity<ScoreResponse> submitScore(
            @RequestBody SubmitScoreRequest request) {
        ScoreResponse response = scoreService.submitScore(
                request.getPlayerId(), request.getScore(), request.getGameMode());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

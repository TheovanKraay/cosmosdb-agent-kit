package com.example.leaderboard.controller;

import com.example.leaderboard.dto.ScoreResponse;
import com.example.leaderboard.dto.SubmitScoreRequest;
import com.example.leaderboard.service.ScoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    public ResponseEntity<ScoreResponse> submitScore(@RequestBody SubmitScoreRequest request) {
        ScoreResponse response = scoreService.submitScore(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

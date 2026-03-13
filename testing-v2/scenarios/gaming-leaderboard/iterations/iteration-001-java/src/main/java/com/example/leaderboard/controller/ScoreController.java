package com.example.leaderboard.controller;

import com.example.leaderboard.model.ScoreResponse;
import com.example.leaderboard.model.SubmitScoreRequest;
import com.example.leaderboard.repository.CosmosRepository;
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

    private final CosmosRepository repository;

    @Autowired
    public ScoreController(CosmosRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<ScoreResponse> submitScore(@RequestBody SubmitScoreRequest request) {
        ScoreResponse response = repository.submitScore(
                request.getPlayerId(),
                request.getScore(),
                request.getGameMode()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

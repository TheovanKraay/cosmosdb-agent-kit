package com.example.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for POST /api/scores.
 */
public class ScoreResponse {

    @JsonProperty("scoreId")
    private String scoreId;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("score")
    private int score;

    public ScoreResponse() {}

    public ScoreResponse(String scoreId, String playerId, int score) {
        this.scoreId = scoreId;
        this.playerId = playerId;
        this.score = score;
    }

    public String getScoreId() { return scoreId; }
    public String getPlayerId() { return playerId; }
    public int getScore() { return score; }
}

package com.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScoreResponse {

    @JsonProperty("scoreId")
    private String scoreId;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("score")
    private int score;

    public ScoreResponse() {
    }

    public ScoreResponse(String scoreId, String playerId, int score) {
        this.scoreId = scoreId;
        this.playerId = playerId;
        this.score = score;
    }

    public String getScoreId() { return scoreId; }
    public void setScoreId(String scoreId) { this.scoreId = scoreId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}

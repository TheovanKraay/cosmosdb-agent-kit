package com.example.leaderboard.model;

public class ScoreResponse {
    private String scoreId;
    private String playerId;
    private int score;

    public ScoreResponse() {}

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

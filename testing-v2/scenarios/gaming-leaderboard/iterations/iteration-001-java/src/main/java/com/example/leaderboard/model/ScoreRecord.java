package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScoreRecord {

    @JsonProperty("id")
    private String id;

    @JsonProperty("scoreId")
    private String scoreId;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("score")
    private int score;

    @JsonProperty("gameMode")
    private String gameMode;

    @JsonProperty("timestamp")
    private String timestamp;

    public ScoreRecord() {}

    public ScoreRecord(String scoreId, String playerId, int score, String gameMode, String timestamp) {
        this.id = scoreId;
        this.scoreId = scoreId;
        this.playerId = playerId;
        this.score = score;
        this.gameMode = gameMode;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getScoreId() { return scoreId; }
    public void setScoreId(String scoreId) { this.scoreId = scoreId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}

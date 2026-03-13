package com.gaming.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Score {

    private String id;
    private String scoreId;
    private String playerId;
    private int score;
    private String gameMode;
    private String timestamp;

    public Score() {}

    public Score(String scoreId, String playerId, int score, String gameMode) {
        this.id = scoreId;
        this.scoreId = scoreId;
        this.playerId = playerId;
        this.score = score;
        this.gameMode = gameMode;
        this.timestamp = java.time.Instant.now().toString();
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

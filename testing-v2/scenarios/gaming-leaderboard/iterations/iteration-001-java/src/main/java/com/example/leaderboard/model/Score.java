package com.example.leaderboard.model;

public class Score {
    private String id;
    private String playerId;
    private int score;
    private String gameMode;
    private String timestamp;

    public Score() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}

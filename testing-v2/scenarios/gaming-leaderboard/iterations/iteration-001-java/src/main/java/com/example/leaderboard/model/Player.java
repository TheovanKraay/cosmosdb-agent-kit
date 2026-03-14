package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Player {

    @JsonProperty("id")
    private String id;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("region")
    private String region;

    @JsonProperty("totalGames")
    private int totalGames;

    @JsonProperty("bestScore")
    private int bestScore;

    @JsonProperty("averageScore")
    private double averageScore;

    @JsonProperty("totalScore")
    private long totalScore;

    public Player() {}

    public Player(String playerId, String displayName, String region) {
        this.id = playerId;
        this.playerId = playerId;
        this.displayName = displayName;
        this.region = region;
        this.totalGames = 0;
        this.bestScore = 0;
        this.averageScore = 0.0;
        this.totalScore = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getBestScore() { return bestScore; }
    public void setBestScore(int bestScore) { this.bestScore = bestScore; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public long getTotalScore() { return totalScore; }
    public void setTotalScore(long totalScore) { this.totalScore = totalScore; }
}

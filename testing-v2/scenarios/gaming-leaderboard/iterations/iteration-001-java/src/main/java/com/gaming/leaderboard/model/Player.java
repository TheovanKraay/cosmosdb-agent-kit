package com.gaming.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {

    private String id;
    private String playerId;
    private String displayName;
    private String region;
    private int totalGames;
    private int bestScore;
    private double averageScore;
    private double totalScore;

    public Player() {}

    public Player(String playerId, String displayName, String region) {
        this.id = playerId;
        this.playerId = playerId;
        this.displayName = displayName;
        this.region = region;
        this.totalGames = 0;
        this.bestScore = 0;
        this.averageScore = 0.0;
        this.totalScore = 0.0;
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

    public double getTotalScore() { return totalScore; }
    public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
}

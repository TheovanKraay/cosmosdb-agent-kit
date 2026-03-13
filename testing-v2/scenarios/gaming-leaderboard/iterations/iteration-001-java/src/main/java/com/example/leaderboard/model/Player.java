package com.example.leaderboard.model;

public class Player {
    private String id;
    private String playerId;
    private String displayName;
    private String region;
    private int totalGames;
    private int bestScore;
    private double averageScore;
    private long totalScore;

    public Player() {}

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

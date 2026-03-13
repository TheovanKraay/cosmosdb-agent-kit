package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Player profile document stored in the "players" container.
 * Partition key: /playerId
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Player {

    private String id;          // same as playerId (Cosmos DB required field)
    private String playerId;
    private String displayName;
    private String region;
    private int totalGames;
    private int bestScore;
    private double averageScore;
    private long totalScore;    // internal: used to compute averageScore
    private String type = "player";

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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

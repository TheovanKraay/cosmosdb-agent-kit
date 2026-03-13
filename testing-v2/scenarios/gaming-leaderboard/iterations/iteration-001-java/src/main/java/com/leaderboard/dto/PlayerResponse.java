package com.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.leaderboard.model.Player;

public class PlayerResponse {

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

    public PlayerResponse() {
    }

    public PlayerResponse(Player player) {
        this.playerId = player.getPlayerId();
        this.displayName = player.getDisplayName();
        this.region = player.getRegion();
        this.totalGames = player.getTotalGames();
        this.bestScore = player.getBestScore();
        this.averageScore = player.getAverageScore();
    }

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
}

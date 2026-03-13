package com.example.leaderboard.dto;

import com.example.leaderboard.model.Player;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for player profile endpoints.
 * camelCase field names as required by the API contract.
 */
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

    public PlayerResponse() {}

    public static PlayerResponse from(Player player) {
        PlayerResponse r = new PlayerResponse();
        r.playerId = player.getPlayerId();
        r.displayName = player.getDisplayName();
        r.region = player.getRegion();
        r.totalGames = player.getTotalGames();
        r.bestScore = player.getBestScore();
        r.averageScore = player.getAverageScore();
        return r;
    }

    public String getPlayerId() { return playerId; }
    public String getDisplayName() { return displayName; }
    public String getRegion() { return region; }
    public int getTotalGames() { return totalGames; }
    public int getBestScore() { return bestScore; }
    public double getAverageScore() { return averageScore; }
}

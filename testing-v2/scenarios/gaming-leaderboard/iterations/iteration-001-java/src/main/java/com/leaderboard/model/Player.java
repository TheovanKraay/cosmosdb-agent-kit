package com.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Player profile document stored in the "players" container.
 * Partition key: playerId — high cardinality, one logical partition per player (Rule 2.4).
 * Using String id = playerId for O(1) point reads (Rule 4.18 / efficient access).
 * averageScore stored as Double (not BigDecimal) for JDK 17+ compatibility (Rule 1.5).
 */
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

    @JsonProperty("type")
    private String type = "player";

    @JsonProperty("_etag")
    private String etag;

    public Player() {
    }

    public Player(String playerId, String displayName, String region) {
        this.id = playerId;
        this.playerId = playerId;
        this.displayName = displayName;
        this.region = region;
        this.totalGames = 0;
        this.bestScore = 0;
        this.averageScore = 0.0;
        this.type = "player";
    }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }

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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

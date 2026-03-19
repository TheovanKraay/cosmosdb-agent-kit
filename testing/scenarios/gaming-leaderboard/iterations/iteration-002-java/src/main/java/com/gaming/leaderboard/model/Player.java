package com.gaming.leaderboard.model;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Player profile with cumulative stats.
 * 
 * Container: players
 * Partition key: /id (playerId) — Rule 2.4: high cardinality, one doc per player
 * 
 * Applied rules:
 * - Rule 1.3: Stats embedded directly (always read with player)
 * - Rule 1.1: Bounded document size (single player profile)
 * - Rule 1.6: Type discriminator for polymorphic container design
 * - Rule 1.5: Schema version for future evolution
 */
@Container(containerName = "players")
public class Player {

    @JsonProperty("id")
    private String id;

    @PartitionKey
    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("type")
    private String type = "player";

    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("country")
    private String country;

    @JsonProperty("totalGamesPlayed")
    private long totalGamesPlayed;

    @JsonProperty("bestScore")
    private long bestScore;

    @JsonProperty("totalScore")
    private long totalScore;

    @JsonProperty("averageScore")
    private double averageScore;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("_etag")
    private String etag;

    public Player() {
    }

    public Player(String displayName, String country) {
        this.id = UUID.randomUUID().toString();
        this.playerId = this.id;
        this.displayName = displayName;
        this.country = country;
        this.totalGamesPlayed = 0;
        this.bestScore = 0;
        this.totalScore = 0;
        this.averageScore = 0.0;
        this.createdAt = Instant.now().toString();
        this.updatedAt = this.createdAt;
    }

    /**
     * Update cumulative stats when a new score is submitted.
     * Rule 1.2: Denormalized stats for read-heavy profile queries.
     */
    public void updateStats(long newScore) {
        this.totalGamesPlayed++;
        this.totalScore += newScore;
        if (newScore > this.bestScore) {
            this.bestScore = newScore;
        }
        this.averageScore = (double) this.totalScore / this.totalGamesPlayed;
        this.updatedAt = Instant.now().toString();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public long getTotalGamesPlayed() { return totalGamesPlayed; }
    public void setTotalGamesPlayed(long totalGamesPlayed) { this.totalGamesPlayed = totalGamesPlayed; }

    public long getBestScore() { return bestScore; }
    public void setBestScore(long bestScore) { this.bestScore = bestScore; }

    public long getTotalScore() { return totalScore; }
    public void setTotalScore(long totalScore) { this.totalScore = totalScore; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}

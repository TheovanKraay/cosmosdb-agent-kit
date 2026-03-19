package com.gaming.leaderboard.model;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Individual game score submission.
 * 
 * Container: scores
 * Partition key: /playerId — Rule 2.5: align with query pattern (player's scores)
 * 
 * Applied rules:
 * - Rule 2.2: playerId distributes writes across many partitions (avoids hot spots)
 * - Rule 2.4: High cardinality (500K unique players)
 * - Rule 1.6: Type discriminator
 */
@Container(containerName = "scores")
public class GameScore {

    @JsonProperty("id")
    private String id;

    @PartitionKey
    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("type")
    private String type = "score";

    @JsonProperty("score")
    private long score;

    @JsonProperty("gameMode")
    private String gameMode;

    @JsonProperty("country")
    private String country;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("weekPeriod")
    private String weekPeriod;

    @JsonProperty("submittedAt")
    private String submittedAt;

    public GameScore() {
    }

    public GameScore(String playerId, long score, String gameMode,
                     String country, String displayName, String weekPeriod) {
        this.id = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.score = score;
        this.gameMode = gameMode;
        this.country = country;
        this.displayName = displayName;
        this.weekPeriod = weekPeriod;
        this.submittedAt = Instant.now().toString();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getScore() { return score; }
    public void setScore(long score) { this.score = score; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getWeekPeriod() { return weekPeriod; }
    public void setWeekPeriod(String weekPeriod) { this.weekPeriod = weekPeriod; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
}

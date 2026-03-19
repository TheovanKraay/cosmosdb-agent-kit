package com.gaming.leaderboard.model;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Denormalized leaderboard entry — materialized view for efficient top-N queries.
 * 
 * Container: leaderboard
 * Partition key: /partitionKey — a composite of scope (global/country) + weekPeriod
 * 
 * Applied rules:
 * - Rule 1.2: Denormalized for read-heavy leaderboard queries
 * - Rule 9.1: Materialized view pattern (updated on score submit)
 * - Rule 9.2: Efficient ranking — pre-sorted entries, avoid cross-partition scans
 * - Rule 2.5: Partition key aligned with query pattern (leaderboard scope + period)
 * - Rule 5.1: Composite index on (partitionKey, score DESC) for ORDER BY
 * - Rule 2.6: Synthetic partition key combining scope + period
 */
@Container(containerName = "leaderboard")
public class LeaderboardEntry {

    @JsonProperty("id")
    private String id;

    @PartitionKey
    @JsonProperty("partitionKey")
    private String partitionKey;

    @JsonProperty("type")
    private String type = "leaderboardEntry";

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("country")
    private String country;

    @JsonProperty("score")
    private long score;

    @JsonProperty("weekPeriod")
    private String weekPeriod;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("updatedAt")
    private String updatedAt;

    public LeaderboardEntry() {
    }

    /**
     * Create a leaderboard entry with a synthetic partition key.
     * Rule 2.6: Synthetic key = scope + "_" + weekPeriod
     *   e.g. "global_2026-W07" or "US_2026-W07"
     */
    public LeaderboardEntry(String playerId, String displayName, String country,
                            long score, String weekPeriod, String scope) {
        this.id = playerId + "_" + scope + "_" + weekPeriod;
        this.partitionKey = scope + "_" + weekPeriod;
        this.playerId = playerId;
        this.displayName = displayName;
        this.country = country;
        this.score = score;
        this.weekPeriod = weekPeriod;
        this.scope = scope;
        this.updatedAt = Instant.now().toString();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPartitionKey() { return partitionKey; }
    public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public long getScore() { return score; }
    public void setScore(long score) { this.score = score; }

    public String getWeekPeriod() { return weekPeriod; }
    public void setWeekPeriod(String weekPeriod) { this.weekPeriod = weekPeriod; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

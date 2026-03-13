package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Leaderboard entry in the materialized "leaderboard" container.
 * Partition key: /leaderboardKey  ("global" or region code)
 *
 * Rule 9.1: Materialized view updated on score submission avoids expensive
 *           cross-partition queries when serving leaderboard reads.
 *
 * id == playerId within a partition, enabling O(1) point reads.
 * Two entries per player: one in "global" partition, one in region partition.
 */
public class LeaderboardEntry {

    @JsonProperty("id")
    private String id;              // = playerId

    @JsonProperty("leaderboardKey")
    private String leaderboardKey;  // partition key: "global" or region

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("score")
    private int score;              // player's current best score

    // Transient: computed after query, never stored in Cosmos DB
    @JsonIgnore
    private int rankPosition;

    public LeaderboardEntry() {}

    public LeaderboardEntry(String leaderboardKey, String playerId, String displayName, int score) {
        this.id = playerId;
        this.leaderboardKey = leaderboardKey;
        this.playerId = playerId;
        this.displayName = displayName;
        this.score = score;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLeaderboardKey() { return leaderboardKey; }
    public void setLeaderboardKey(String leaderboardKey) { this.leaderboardKey = leaderboardKey; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getRankPosition() { return rankPosition; }
    public void setRankPosition(int rankPosition) { this.rankPosition = rankPosition; }
}

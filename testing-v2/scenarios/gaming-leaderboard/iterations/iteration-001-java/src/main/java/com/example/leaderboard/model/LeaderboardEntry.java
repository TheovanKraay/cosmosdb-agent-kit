package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Leaderboard entry in the materialized view "leaderboard" container.
 * Partition key: /leaderboardKey  (e.g. "global", "region-US", "region-EU")
 *
 * This is a denormalized copy of player+score data optimized for top-N reads.
 * It is updated whenever a player's bestScore is beaten.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeaderboardEntry {

    private String id;              // playerId — unique within leaderboardKey partition
    private String leaderboardKey;  // "global" or "region-{region}"
    private String playerId;
    private String displayName;
    private int bestScore;
    private String type = "leaderboardEntry";

    public LeaderboardEntry() {}

    public LeaderboardEntry(String leaderboardKey, String playerId, String displayName, int bestScore) {
        this.id = playerId;
        this.leaderboardKey = leaderboardKey;
        this.playerId = playerId;
        this.displayName = displayName;
        this.bestScore = bestScore;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLeaderboardKey() { return leaderboardKey; }
    public void setLeaderboardKey(String leaderboardKey) { this.leaderboardKey = leaderboardKey; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getBestScore() { return bestScore; }
    public void setBestScore(int bestScore) { this.bestScore = bestScore; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

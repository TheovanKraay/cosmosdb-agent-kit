package com.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Materialized leaderboard entry stored in the "leaderboard-entries" container.
 *
 * Partition key: leaderboardKey — "global" for global leaderboard or region code
 * (e.g. "US") for regional leaderboards. All entries for a leaderboard are co-located
 * in a single partition, enabling efficient ORDER BY score DESC queries without
 * cross-partition fan-out (Rule 9.1 — materialized view pattern).
 *
 * id = playerId, unique within each leaderboardKey partition, enabling O(1) upsert
 * when the player's best score improves.
 */
public class LeaderboardEntry {

    @JsonProperty("id")
    private String id;

    @JsonProperty("leaderboardKey")
    private String leaderboardKey;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("score")
    private int score;

    @JsonProperty("region")
    private String region;

    @JsonProperty("type")
    private String type = "leaderboardEntry";

    public LeaderboardEntry() {
    }

    public LeaderboardEntry(String leaderboardKey, String playerId,
                            String displayName, int score, String region) {
        this.id = playerId;
        this.leaderboardKey = leaderboardKey;
        this.playerId = playerId;
        this.displayName = displayName;
        this.score = score;
        this.region = region;
        this.type = "leaderboardEntry";
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

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

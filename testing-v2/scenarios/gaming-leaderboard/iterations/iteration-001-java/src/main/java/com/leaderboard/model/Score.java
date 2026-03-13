package com.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Individual score submission stored in the "scores" container.
 * Partition key: playerId — co-locates all scores for a player, enables
 * efficient per-player history queries without cross-partition fan-out (Rule 2.6).
 */
public class Score {

    @JsonProperty("id")
    private String id;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("score")
    private int score;

    @JsonProperty("gameMode")
    private String gameMode;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("type")
    private String type = "score";

    public Score() {
    }

    public Score(String id, String playerId, int score, String gameMode, String timestamp) {
        this.id = id;
        this.playerId = playerId;
        this.score = score;
        this.gameMode = gameMode;
        this.timestamp = timestamp;
        this.type = "score";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

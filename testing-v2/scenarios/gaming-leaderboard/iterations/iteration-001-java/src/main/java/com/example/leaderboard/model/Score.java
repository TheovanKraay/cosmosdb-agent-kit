package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Score document stored in the "scores" container.
 * Partition key: /playerId — all scores for a player are co-located.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Score {

    private String id;          // same as scoreId (Cosmos DB required field)
    private String scoreId;
    private String playerId;
    private int score;
    private String gameMode;    // optional
    private String timestamp;
    private String type = "score";

    public Score() {}

    public Score(String scoreId, String playerId, int score, String gameMode, String timestamp) {
        this.id = scoreId;
        this.scoreId = scoreId;
        this.playerId = playerId;
        this.score = score;
        this.gameMode = gameMode;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getScoreId() { return scoreId; }
    public void setScoreId(String scoreId) { this.scoreId = scoreId; }

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

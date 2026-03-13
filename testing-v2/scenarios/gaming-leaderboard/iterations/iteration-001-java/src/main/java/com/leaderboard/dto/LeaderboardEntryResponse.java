package com.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LeaderboardEntryResponse {

    @JsonProperty("rank")
    private int rank;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("score")
    private int score;

    public LeaderboardEntryResponse() {
    }

    public LeaderboardEntryResponse(int rank, String playerId, String displayName, int score) {
        this.rank = rank;
        this.playerId = playerId;
        this.displayName = displayName;
        this.score = score;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}

package com.gaming.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LeaderboardEntry {

    private int rank;
    private String playerId;
    private String displayName;
    private int score;

    public LeaderboardEntry() {}

    public LeaderboardEntry(int rank, String playerId, String displayName, int score) {
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

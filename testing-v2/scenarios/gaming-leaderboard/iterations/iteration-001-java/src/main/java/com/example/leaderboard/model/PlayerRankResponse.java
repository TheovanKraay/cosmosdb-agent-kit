package com.example.leaderboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PlayerRankResponse {

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("rank")
    private int rank;

    @JsonProperty("score")
    private int score;

    @JsonProperty("neighbors")
    private List<LeaderboardEntry> neighbors;

    public PlayerRankResponse() {}

    public PlayerRankResponse(String playerId, int rank, int score, List<LeaderboardEntry> neighbors) {
        this.playerId = playerId;
        this.rank = rank;
        this.score = score;
        this.neighbors = neighbors;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<LeaderboardEntry> getNeighbors() { return neighbors; }
    public void setNeighbors(List<LeaderboardEntry> neighbors) { this.neighbors = neighbors; }
}

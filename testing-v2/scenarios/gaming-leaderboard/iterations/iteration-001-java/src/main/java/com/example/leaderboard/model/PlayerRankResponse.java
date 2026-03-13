package com.example.leaderboard.model;

import java.util.List;

public class PlayerRankResponse {
    private String playerId;
    private int rank;
    private int score;
    private List<LeaderboardEntry> neighbors;

    public PlayerRankResponse() {}

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<LeaderboardEntry> getNeighbors() { return neighbors; }
    public void setNeighbors(List<LeaderboardEntry> neighbors) { this.neighbors = neighbors; }
}

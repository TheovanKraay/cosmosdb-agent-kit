package com.example.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response body for GET /api/players/{playerId}/rank.
 */
public class PlayerRankResponse {

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("rank")
    private int rank;

    @JsonProperty("score")
    private int score;

    @JsonProperty("neighbors")
    private List<LeaderboardEntryResponse> neighbors;

    public PlayerRankResponse() {}

    public PlayerRankResponse(String playerId, int rank, int score, List<LeaderboardEntryResponse> neighbors) {
        this.playerId = playerId;
        this.rank = rank;
        this.score = score;
        this.neighbors = neighbors;
    }

    public String getPlayerId() { return playerId; }
    public int getRank() { return rank; }
    public int getScore() { return score; }
    public List<LeaderboardEntryResponse> getNeighbors() { return neighbors; }
}

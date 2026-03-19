package com.gaming.leaderboard.dto;

import java.util.List;

/**
 * Response DTO for player rank with nearby players (±10 positions).
 */
public class PlayerRankResponse {

    private String playerId;
    private String displayName;
    private long score;
    private int rank;
    private List<RankedPlayer> nearbyPlayers;

    public PlayerRankResponse() {
    }

    public PlayerRankResponse(String playerId, String displayName, long score,
                              int rank, List<RankedPlayer> nearbyPlayers) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.score = score;
        this.rank = rank;
        this.nearbyPlayers = nearbyPlayers;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public long getScore() { return score; }
    public void setScore(long score) { this.score = score; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public List<RankedPlayer> getNearbyPlayers() { return nearbyPlayers; }
    public void setNearbyPlayers(List<RankedPlayer> nearbyPlayers) { this.nearbyPlayers = nearbyPlayers; }

    public static class RankedPlayer {
        private String playerId;
        private String displayName;
        private long score;
        private int rank;

        public RankedPlayer() {
        }

        public RankedPlayer(String playerId, String displayName, long score, int rank) {
            this.playerId = playerId;
            this.displayName = displayName;
            this.score = score;
            this.rank = rank;
        }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public long getScore() { return score; }
        public void setScore(long score) { this.score = score; }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
    }
}

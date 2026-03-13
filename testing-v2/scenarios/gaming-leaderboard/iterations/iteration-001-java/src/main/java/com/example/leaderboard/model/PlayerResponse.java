package com.example.leaderboard.model;

public class PlayerResponse {
    private String playerId;
    private String displayName;
    private String region;
    private int totalGames;
    private int bestScore;
    private double averageScore;

    public PlayerResponse() {}

    public static PlayerResponse fromPlayer(Player player) {
        PlayerResponse response = new PlayerResponse();
        response.setPlayerId(player.getPlayerId());
        response.setDisplayName(player.getDisplayName());
        response.setRegion(player.getRegion());
        response.setTotalGames(player.getTotalGames());
        response.setBestScore(player.getBestScore());
        response.setAverageScore(player.getAverageScore());
        return response;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getBestScore() { return bestScore; }
    public void setBestScore(int bestScore) { this.bestScore = bestScore; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }
}

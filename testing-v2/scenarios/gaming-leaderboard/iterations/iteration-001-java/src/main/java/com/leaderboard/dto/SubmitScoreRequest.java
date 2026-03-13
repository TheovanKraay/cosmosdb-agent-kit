package com.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubmitScoreRequest {

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("score")
    private int score;

    @JsonProperty("gameMode")
    private String gameMode;

    public SubmitScoreRequest() {
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
}

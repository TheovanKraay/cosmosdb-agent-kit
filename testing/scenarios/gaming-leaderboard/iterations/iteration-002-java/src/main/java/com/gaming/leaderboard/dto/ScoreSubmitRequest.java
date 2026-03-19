package com.gaming.leaderboard.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for submitting a new score.
 */
public class ScoreSubmitRequest {

    @NotBlank(message = "playerId is required")
    private String playerId;

    @NotNull(message = "score is required")
    @Min(value = 0, message = "score must be non-negative")
    private Long score;

    private String gameMode;

    public ScoreSubmitRequest() {
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
}

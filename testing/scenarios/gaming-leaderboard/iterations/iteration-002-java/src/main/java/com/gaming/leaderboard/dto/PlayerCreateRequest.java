package com.gaming.leaderboard.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new player profile.
 */
public class PlayerCreateRequest {

    @NotBlank(message = "displayName is required")
    private String displayName;

    @NotBlank(message = "country is required")
    private String country;

    public PlayerCreateRequest() {
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}

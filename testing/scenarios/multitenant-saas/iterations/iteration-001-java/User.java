package com.example.multitenentsaas.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * User entity within a tenant.
 * Partition key: /tenantId=<id>, /type="user", /projectId="" (empty for non-project entities)
 */
public class User extends BaseEntity {

    @JsonProperty("email")
    private String email;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("role")
    private String role; // "admin", "member", "viewer"

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("isActive")
    private boolean isActive = true;

    public User() {
        setType("user");
        setProjectId(""); // No project context for user-level entities
    }

    // Getters and setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}

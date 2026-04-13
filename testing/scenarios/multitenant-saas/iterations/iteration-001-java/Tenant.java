package com.example.multitenentsaas.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Tenant (company) entity.
 * Partition key: /tenantId=<id>, /type="tenant", /projectId="" (empty for non-project entities)
 */
public class Tenant extends BaseEntity {

    @JsonProperty("name")
    private String name;

    @JsonProperty("plan")
    private String plan; // "free", "pro", "enterprise"

    @JsonProperty("maxUsers")
    private int maxUsers;

    @JsonProperty("maxProjects")
    private int maxProjects;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("isActive")
    private boolean isActive = true;

    public Tenant() {
        setType("tenant");
        setProjectId(""); // No project context for tenant-level entities
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public int getMaxUsers() { return maxUsers; }
    public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }

    public int getMaxProjects() { return maxProjects; }
    public void setMaxProjects(int maxProjects) { this.maxProjects = maxProjects; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}

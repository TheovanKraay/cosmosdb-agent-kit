package com.example.multitenentsaas.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Project entity within a tenant.
 * Partition key: /tenantId=<id>, /type="project", /projectId=<self-id>
 *
 * Denormalized task counts (Rule 1.2) for efficient tenant-level analytics.
 */
public class Project extends BaseEntity {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status; // "active", "archived", "completed"

    @JsonProperty("ownerId")
    private String ownerId;

    /** Denormalized owner name (Rule 1.2) for display without extra queries */
    @JsonProperty("ownerName")
    private String ownerName;

    /** Denormalized task counts for analytics (Rule 1.2) */
    @JsonProperty("taskCountTotal")
    private int taskCountTotal;

    @JsonProperty("taskCountOpen")
    private int taskCountOpen;

    @JsonProperty("taskCountInProgress")
    private int taskCountInProgress;

    @JsonProperty("taskCountCompleted")
    private int taskCountCompleted;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    public Project() {
        setType("project");
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public int getTaskCountTotal() { return taskCountTotal; }
    public void setTaskCountTotal(int taskCountTotal) { this.taskCountTotal = taskCountTotal; }

    public int getTaskCountOpen() { return taskCountOpen; }
    public void setTaskCountOpen(int taskCountOpen) { this.taskCountOpen = taskCountOpen; }

    public int getTaskCountInProgress() { return taskCountInProgress; }
    public void setTaskCountInProgress(int taskCountInProgress) { this.taskCountInProgress = taskCountInProgress; }

    public int getTaskCountCompleted() { return taskCountCompleted; }
    public void setTaskCountCompleted(int taskCountCompleted) { this.taskCountCompleted = taskCountCompleted; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Project entity within a tenant.
 * Stored with type = "project" in multitenant-data container.
 */
public class Project extends BaseDocument {

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("createdAt")
    private String createdAt;

    public Project() {
        setType("project");
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

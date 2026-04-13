package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Task entity within a project.
 * Stored with type = "task" in multitenant-data container.
 */
public class Task extends BaseDocument {

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("assigneeId")
    private String assigneeId;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("status")
    private String status;

    @JsonProperty("createdAt")
    private String createdAt;

    public Task() {
        setType("task");
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

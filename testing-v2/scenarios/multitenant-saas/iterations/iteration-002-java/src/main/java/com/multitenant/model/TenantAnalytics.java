package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TenantAnalytics {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("totalUsers")
    private int totalUsers;

    @JsonProperty("totalProjects")
    private int totalProjects;

    @JsonProperty("totalTasks")
    private int totalTasks;

    @JsonProperty("tasksByStatus")
    private Map<String, Integer> tasksByStatus;

    @JsonProperty("tasksByPriority")
    private Map<String, Integer> tasksByPriority;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public int getTotalUsers() { return totalUsers; }
    public void setTotalUsers(int totalUsers) { this.totalUsers = totalUsers; }

    public int getTotalProjects() { return totalProjects; }
    public void setTotalProjects(int totalProjects) { this.totalProjects = totalProjects; }

    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }

    public Map<String, Integer> getTasksByStatus() { return tasksByStatus; }
    public void setTasksByStatus(Map<String, Integer> tasksByStatus) { this.tasksByStatus = tasksByStatus; }

    public Map<String, Integer> getTasksByPriority() { return tasksByPriority; }
    public void setTasksByPriority(Map<String, Integer> tasksByPriority) { this.tasksByPriority = tasksByPriority; }
}

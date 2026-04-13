package com.example.multitenentsaas.dto;

/**
 * Tenant-level analytics with task counts by status.
 * Aggregated across all projects within a tenant.
 */
public class TenantAnalytics {
    private String tenantId;
    private String tenantName;
    private int totalProjects;
    private int totalTasks;
    private int openTasks;
    private int inProgressTasks;
    private int completedTasks;
    private int cancelledTasks;
    private double completionRate;

    // Getters and setters
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public int getTotalProjects() { return totalProjects; }
    public void setTotalProjects(int totalProjects) { this.totalProjects = totalProjects; }

    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }

    public int getOpenTasks() { return openTasks; }
    public void setOpenTasks(int openTasks) { this.openTasks = openTasks; }

    public int getInProgressTasks() { return inProgressTasks; }
    public void setInProgressTasks(int inProgressTasks) { this.inProgressTasks = inProgressTasks; }

    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }

    public int getCancelledTasks() { return cancelledTasks; }
    public void setCancelledTasks(int cancelledTasks) { this.cancelledTasks = cancelledTasks; }

    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
}

package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tenant entity.
 * Stored with type = "tenant" in multitenant-data container.
 * Partition key: /tenantId + /type (hierarchical).
 */
public class Tenant extends BaseDocument {

    @JsonProperty("name")
    private String name;

    @JsonProperty("plan")
    private String plan;

    @JsonProperty("createdAt")
    private String createdAt;

    public Tenant() {
        setType("tenant");
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

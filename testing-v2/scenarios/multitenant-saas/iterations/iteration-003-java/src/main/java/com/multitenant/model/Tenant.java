package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Tenant extends BaseDocument {
    private String tenantId;
    private String name;
    private String plan;
    private String createdAt;

    public Tenant() {
        setType("tenant");
    }

    @Override
    public String getTenantId() { return tenantId; }
    @Override
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

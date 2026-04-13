package com.multitenant.saas.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
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

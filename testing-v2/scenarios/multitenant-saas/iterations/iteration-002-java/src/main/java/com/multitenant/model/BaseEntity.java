package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Rule 1.5: Handle JSON serialization correctly
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseEntity {

    @JsonProperty("id")
    private String id;

    // HPK Level 1
    @JsonProperty("tenantId")
    private String tenantId;

    // Rule 1.11: Type discriminator for polymorphic data (HPK Level 2)
    @JsonProperty("type")
    private String type;

    // Rule 1.10: Version document schemas
    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    // Rule 4.9: ETag for optimistic concurrency
    @JsonProperty("_etag")
    private String etag;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}

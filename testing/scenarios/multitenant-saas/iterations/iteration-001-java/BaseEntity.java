package com.example.multitenentsaas.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base entity with type discriminator (Rule 1.9) and schema versioning (Rule 1.8).
 * All entities stored in the same container with hierarchical partition keys (Rule 2.3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseEntity {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    /** Type discriminator for polymorphic data (Rule 1.9) */
    @JsonProperty("type")
    private String type;

    /** Schema versioning for safe evolution (Rule 1.8) */
    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    /** Project ID for hierarchical partition key Level 3 */
    @JsonProperty("projectId")
    private String projectId;

    /** ETag for optimistic concurrency (Rule 4.7) */
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

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}

package com.multitenant.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base document with common fields for all entity types.
 * Rule 1.11: Type discriminator for polymorphic data.
 * Rule 1.10: Schema versioning.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseDocument {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
}

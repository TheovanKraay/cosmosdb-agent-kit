package com.iot.telemetry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Device metadata entity stored in the 'devices' container.
 * Partition key: /deviceId (Rule 2.4: high-cardinality, immutable).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Device {

    /** Cosmos DB document id — set to deviceId for point reads. */
    private String id;

    private String deviceId;
    private String name;
    private String location;
    private String deviceType;

    /** Cosmos DB system field for optimistic concurrency (Rule 4.9). */
    @JsonProperty("_etag")
    private String etag;

    public Device() {}

    public Device(String deviceId, String name, String location, String deviceType) {
        this.id = deviceId;
        this.deviceId = deviceId;
        this.name = name;
        this.location = location;
        this.deviceType = deviceType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}

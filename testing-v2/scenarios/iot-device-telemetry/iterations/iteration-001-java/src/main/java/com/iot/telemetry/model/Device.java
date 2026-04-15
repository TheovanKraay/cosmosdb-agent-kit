package com.iot.telemetry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Device entity representing IoT device metadata.
 * Stored in the "devices" container with partition key /deviceId.
 *
 * Best Practices Applied:
 * - Rule 2.4: High-cardinality partition key (deviceId)
 * - Rule 1.6: Type discriminator field
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Device {

    @JsonProperty("id")
    private String id;

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("location")
    private String location;

    @JsonProperty("deviceType")
    private String deviceType;

    @JsonProperty("type")
    private String type = "device";

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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

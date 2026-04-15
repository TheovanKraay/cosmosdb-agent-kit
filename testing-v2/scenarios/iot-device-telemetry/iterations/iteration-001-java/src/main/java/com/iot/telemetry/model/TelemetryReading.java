package com.iot.telemetry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Telemetry reading from an IoT device.
 * Stored in the "telemetry" container with partition key /deviceId.
 *
 * Best Practices Applied:
 * - Rule 2.4: High-cardinality partition key (deviceId)
 * - Rule 1.6: Type discriminator field
 * - Rule 1.5: Schema version for evolution
 * - TTL for 30-day automatic expiration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryReading {

    @JsonProperty("id")
    private String id;

    @JsonProperty("readingId")
    private String readingId;

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("humidity")
    private Double humidity;

    @JsonProperty("batteryLevel")
    private Double batteryLevel;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("type")
    private String type = "telemetry";

    @JsonProperty("schemaVersion")
    private int schemaVersion = 1;

    // TTL: 30 days = 2592000 seconds
    @JsonProperty("ttl")
    private int ttl = 2592000;

    public TelemetryReading() {}

    public void ensureDefaults() {
        if (this.readingId == null) {
            this.readingId = UUID.randomUUID().toString();
        }
        if (this.id == null) {
            this.id = this.readingId;
        }
        if (this.timestamp == null) {
            this.timestamp = Instant.now().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReadingId() { return readingId; }
    public void setReadingId(String readingId) { this.readingId = readingId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }

    public Double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Double batteryLevel) { this.batteryLevel = batteryLevel; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }
}

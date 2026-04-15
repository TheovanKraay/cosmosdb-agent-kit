package com.iot.telemetry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telemetry reading entity stored in the 'telemetry' container.
 * Partition key: /deviceId (aligns with device-level queries, Rule 2.7).
 * TTL: 30 days (configured at container level).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryReading {

    /** Cosmos DB document id — unique readingId (UUID). */
    private String id;

    /** Exposed as readingId in API responses. */
    private String readingId;

    private String deviceId;
    private double temperature;
    private double humidity;
    private double batteryLevel;
    private String timestamp;

    /** TTL in seconds — set per document for fine-grained control. */
    private Integer ttl;

    @JsonProperty("_etag")
    private String etag;

    public TelemetryReading() {}

    public TelemetryReading(String readingId, String deviceId, double temperature,
                            double humidity, double batteryLevel, String timestamp) {
        this.id = readingId;
        this.readingId = readingId;
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.batteryLevel = batteryLevel;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReadingId() { return readingId; }
    public void setReadingId(String readingId) { this.readingId = readingId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getHumidity() { return humidity; }
    public void setHumidity(double humidity) { this.humidity = humidity; }

    public double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(double batteryLevel) { this.batteryLevel = batteryLevel; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Integer getTtl() { return ttl; }
    public void setTtl(Integer ttl) { this.ttl = ttl; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}

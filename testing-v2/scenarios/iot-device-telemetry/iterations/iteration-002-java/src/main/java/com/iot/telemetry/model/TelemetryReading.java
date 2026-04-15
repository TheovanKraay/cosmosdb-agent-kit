package com.iot.telemetry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryReading {

    @JsonProperty("id")
    private String id;

    @JsonProperty("readingId")
    private String readingId;

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("temperature")
    private double temperature;

    @JsonProperty("humidity")
    private double humidity;

    @JsonProperty("batteryLevel")
    private double batteryLevel;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("type")
    private String type = "telemetry";

    @JsonProperty("ttl")
    private int ttl = 30 * 24 * 60 * 60; // 30 days in seconds

    @JsonProperty("_etag")
    private String etag;

    public TelemetryReading() {}

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

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
}

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

    // TTL field for automatic 30-day expiration
    @JsonProperty("ttl")
    private Integer ttl;

    public TelemetryReading() {
    }

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReadingId() {
        return readingId;
    }

    public void setReadingId(String readingId) {
        this.readingId = readingId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }
}

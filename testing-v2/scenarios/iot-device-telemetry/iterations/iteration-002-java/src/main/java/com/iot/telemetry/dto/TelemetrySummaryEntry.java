package com.iot.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TelemetrySummaryEntry {

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

    public TelemetrySummaryEntry() {}

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
}

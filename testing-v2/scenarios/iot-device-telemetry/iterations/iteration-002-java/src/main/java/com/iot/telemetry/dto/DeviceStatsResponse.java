package com.iot.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceStatsResponse {

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("period")
    private String period;

    @JsonProperty("temperature")
    private StatValues temperature;

    @JsonProperty("humidity")
    private StatValues humidity;

    @JsonProperty("batteryLevel")
    private StatValues batteryLevel;

    public DeviceStatsResponse() {}

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public StatValues getTemperature() { return temperature; }
    public void setTemperature(StatValues temperature) { this.temperature = temperature; }

    public StatValues getHumidity() { return humidity; }
    public void setHumidity(StatValues humidity) { this.humidity = humidity; }

    public StatValues getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(StatValues batteryLevel) { this.batteryLevel = batteryLevel; }

    public static class StatValues {
        @JsonProperty("min")
        private double min;

        @JsonProperty("max")
        private double max;

        @JsonProperty("avg")
        private double avg;

        public StatValues() {}

        public StatValues(double min, double max, double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }

        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }

        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }

        public double getAvg() { return avg; }
        public void setAvg(double avg) { this.avg = avg; }
    }
}

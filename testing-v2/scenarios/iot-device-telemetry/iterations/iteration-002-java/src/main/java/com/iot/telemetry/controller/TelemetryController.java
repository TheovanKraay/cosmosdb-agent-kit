package com.iot.telemetry.controller;

import com.iot.telemetry.dto.BatchIngestResponse;
import com.iot.telemetry.dto.DeviceStatsResponse;
import com.iot.telemetry.dto.TelemetrySummaryEntry;
import com.iot.telemetry.model.Device;
import com.iot.telemetry.model.TelemetryReading;
import com.iot.telemetry.repository.DeviceRepository;
import com.iot.telemetry.repository.TelemetryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class TelemetryController {

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;

    public TelemetryController(TelemetryRepository telemetryRepository,
                               DeviceRepository deviceRepository) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
    }

    @PostMapping("/api/telemetry")
    public ResponseEntity<Map<String, Object>> ingestTelemetry(
            @RequestBody TelemetryReading reading) {
        TelemetryReading created = telemetryRepository.ingestReading(reading);
        return ResponseEntity.status(HttpStatus.CREATED).body(toReadingResponse(created));
    }

    @PostMapping("/api/telemetry/batch")
    public ResponseEntity<BatchIngestResponse> ingestBatch(
            @RequestBody List<TelemetryReading> readings) {
        int count = telemetryRepository.ingestBatch(readings);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BatchIngestResponse(count));
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<Map<String, Object>> getLatestReading(
            @PathVariable String deviceId) {
        TelemetryReading latest = telemetryRepository.getLatestReading(deviceId);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toReadingResponse(latest));
    }

    @GetMapping("/api/devices/{deviceId}/telemetry")
    public ResponseEntity<List<Map<String, Object>>> getReadingsByTimeRange(
            @PathVariable String deviceId,
            @RequestParam String start,
            @RequestParam String end) {
        List<TelemetryReading> readings = telemetryRepository.getReadingsByTimeRange(
                deviceId, start, end);
        List<Map<String, Object>> response = readings.stream()
                .map(this::toReadingResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/stats")
    public ResponseEntity<DeviceStatsResponse> getDeviceStats(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24h") String period) {
        DeviceStatsResponse stats = telemetryRepository.getDeviceStats(deviceId, period);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/locations/{location}/telemetry/latest")
    public ResponseEntity<List<TelemetrySummaryEntry>> getLocationSummary(
            @PathVariable String location) {
        // Get all devices at this location
        List<Device> devices = deviceRepository.getDevicesByLocation(location);
        List<String> deviceIds = devices.stream()
                .map(Device::getDeviceId)
                .collect(Collectors.toList());

        List<TelemetrySummaryEntry> summary = telemetryRepository.getLocationSummary(
                location, deviceIds);
        return ResponseEntity.ok(summary);
    }

    private Map<String, Object> toReadingResponse(TelemetryReading reading) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("readingId", reading.getReadingId());
        response.put("deviceId", reading.getDeviceId());
        response.put("temperature", reading.getTemperature());
        response.put("humidity", reading.getHumidity());
        response.put("batteryLevel", reading.getBatteryLevel());
        response.put("timestamp", reading.getTimestamp());
        return response;
    }
}

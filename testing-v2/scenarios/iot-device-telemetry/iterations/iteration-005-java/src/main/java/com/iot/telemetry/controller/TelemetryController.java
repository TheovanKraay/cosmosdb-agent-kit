package com.iot.telemetry.controller;

import com.iot.telemetry.model.Device;
import com.iot.telemetry.model.TelemetryReading;
import com.iot.telemetry.repository.DeviceRepository;
import com.iot.telemetry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class TelemetryController {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryController.class);
    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;

    public TelemetryController(TelemetryRepository telemetryRepository, DeviceRepository deviceRepository) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
    }

    @PostMapping("/api/telemetry")
    public ResponseEntity<?> ingestTelemetry(@RequestBody(required = false) TelemetryReading reading) {
        if (reading == null || reading.getDeviceId() == null || reading.getDeviceId().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "deviceId is required"));
        }
        // Validate device exists
        Device device = deviceRepository.getDevice(reading.getDeviceId());
        if (device == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Device not found: " + reading.getDeviceId()));
        }
        try {
            TelemetryReading created = telemetryRepository.ingestReading(reading);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Error ingesting telemetry: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to ingest telemetry: " + e.getMessage()));
        }
    }

    @PostMapping("/api/telemetry/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody List<TelemetryReading> readings) {
        try {
            int ingested = telemetryRepository.ingestBatch(readings);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ingested", ingested));
        } catch (Exception e) {
            logger.error("Error in batch ingestion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to ingest batch: " + e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<?> getLatestReading(@PathVariable String deviceId) {
        try {
            TelemetryReading latest = telemetryRepository.getLatestReading(deviceId);
            if (latest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No readings found for device: " + deviceId));
            }
            return ResponseEntity.ok(latest);
        } catch (Exception e) {
            logger.error("Error getting latest reading for {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get latest reading: " + e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry")
    public ResponseEntity<?> getReadingsByTimeRange(@PathVariable String deviceId,
                                                     @RequestParam String start,
                                                     @RequestParam String end) {
        try {
            List<TelemetryReading> readings = telemetryRepository.getReadingsByTimeRange(deviceId, start, end);
            return ResponseEntity.ok(readings);
        } catch (Exception e) {
            logger.error("Error querying time range for {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to query time range: " + e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/stats")
    public ResponseEntity<?> getDeviceStats(@PathVariable String deviceId,
                                             @RequestParam(defaultValue = "24h") String period) {
        try {
            List<TelemetryReading> readings = telemetryRepository.getReadingsForStats(deviceId, period);

            if (readings.isEmpty()) {
                // Return zeros if no readings
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("deviceId", deviceId);
                stats.put("period", period);
                stats.put("temperature", Map.of("min", 0.0, "max", 0.0, "avg", 0.0));
                stats.put("humidity", Map.of("min", 0.0, "max", 0.0, "avg", 0.0));
                stats.put("batteryLevel", Map.of("min", 0.0, "max", 0.0, "avg", 0.0));
                return ResponseEntity.ok(stats);
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("deviceId", deviceId);
            stats.put("period", period);
            stats.put("temperature", computeStats(readings, TelemetryReading::getTemperature));
            stats.put("humidity", computeStats(readings, TelemetryReading::getHumidity));
            stats.put("batteryLevel", computeStats(readings, TelemetryReading::getBatteryLevel));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error computing stats for {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to compute stats: " + e.getMessage()));
        }
    }

    @GetMapping("/api/locations/{location}/telemetry/latest")
    public ResponseEntity<?> getLocationSummary(@PathVariable String location) {
        try {
            List<Device> devices = deviceRepository.getDevicesByLocation(location);
            if (devices.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Map<String, Object>> summaries = new ArrayList<>();
            for (Device device : devices) {
                TelemetryReading latest = telemetryRepository.getLatestReading(device.getDeviceId());
                if (latest != null) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("deviceId", latest.getDeviceId());
                    entry.put("temperature", latest.getTemperature());
                    entry.put("humidity", latest.getHumidity());
                    entry.put("batteryLevel", latest.getBatteryLevel());
                    entry.put("timestamp", latest.getTimestamp());
                    summaries.add(entry);
                }
            }

            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            logger.error("Error getting location summary for {}: {}", location, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get location summary: " + e.getMessage()));
        }
    }

    private Map<String, Double> computeStats(List<TelemetryReading> readings,
                                              java.util.function.ToDoubleFunction<TelemetryReading> extractor) {
        DoubleSummaryStatistics stats = readings.stream()
                .mapToDouble(extractor)
                .summaryStatistics();

        Map<String, Double> result = new LinkedHashMap<>();
        result.put("min", stats.getMin());
        result.put("max", stats.getMax());
        result.put("avg", Math.round(stats.getAverage() * 100.0) / 100.0);
        return result;
    }
}

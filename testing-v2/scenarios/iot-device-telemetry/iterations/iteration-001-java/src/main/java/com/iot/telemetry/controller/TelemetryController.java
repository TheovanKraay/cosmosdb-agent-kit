package com.iot.telemetry.controller;

import com.iot.telemetry.model.TelemetryReading;
import com.iot.telemetry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * REST Controller for Telemetry ingestion and query endpoints.
 *
 * Endpoints:
 * - POST /api/telemetry                                   → Ingest single reading (201)
 * - POST /api/telemetry/batch                             → Batch ingest readings (201)
 * - GET  /api/devices/{deviceId}/telemetry/latest          → Latest reading (200/404)
 * - GET  /api/devices/{deviceId}/telemetry?start=X&end=Y   → Time range query (200)
 * - GET  /api/devices/{deviceId}/telemetry/stats?period=24h → Aggregate stats (200)
 */
@RestController
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);
    private final TelemetryRepository telemetryRepository;
    private final com.iot.telemetry.repository.DeviceRepository deviceRepository;

    public TelemetryController(TelemetryRepository telemetryRepository,
                               com.iot.telemetry.repository.DeviceRepository deviceRepository) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
    }

    @PostMapping("/api/telemetry")
    public ResponseEntity<?> ingestTelemetry(@RequestBody TelemetryReading reading) {
        try {
            // Validate required fields
            if (reading.getDeviceId() == null || reading.getDeviceId().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "deviceId is required"));
            }
            if (reading.getTemperature() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "temperature is required"));
            }
            if (reading.getHumidity() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "humidity is required"));
            }
            if (reading.getBatteryLevel() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "batteryLevel is required"));
            }

            // Verify device exists
            Boolean exists = deviceRepository.deviceExists(reading.getDeviceId()).block();
            if (exists == null || !exists) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found: " + reading.getDeviceId()));
            }

            reading.ensureDefaults();
            TelemetryReading created = telemetryRepository.createReading(reading).block();
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Error ingesting telemetry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/telemetry/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody List<TelemetryReading> readings) {
        try {
            long count = telemetryRepository.createReadingsBatch(readings).count().block();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("ingested", count));
        } catch (Exception e) {
            log.error("Error in batch telemetry ingestion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<?> getLatestReading(@PathVariable String deviceId) {
        try {
            TelemetryReading latest = telemetryRepository.getLatestReading(deviceId).block();
            if (latest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No readings found for device"));
            }
            return ResponseEntity.ok(latest);
        } catch (Exception e) {
            log.error("Error getting latest reading", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry")
    public ResponseEntity<?> getReadingsByTimeRange(@PathVariable String deviceId,
                                                     @RequestParam String start,
                                                     @RequestParam String end) {
        try {
            List<TelemetryReading> readings = telemetryRepository
                    .getReadingsByTimeRange(deviceId, start, end)
                    .collectList().block();
            return ResponseEntity.ok(readings);
        } catch (Exception e) {
            log.error("Error querying time range", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/stats")
    public ResponseEntity<?> getDeviceStats(@PathVariable String deviceId,
                                            @RequestParam(defaultValue = "24h") String period) {
        try {
            // Parse period into a start time
            String startTime = calculateStartTime(period);

            TelemetryRepository.StatsResult stats = telemetryRepository
                    .getDeviceStats(deviceId, startTime).block();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deviceId", deviceId);
            response.put("period", period);
            response.put("temperature", buildStatObj(
                    stats != null ? stats.getTempMin() : null,
                    stats != null ? stats.getTempMax() : null,
                    stats != null ? stats.getTempAvg() : null));
            response.put("humidity", buildStatObj(
                    stats != null ? stats.getHumMin() : null,
                    stats != null ? stats.getHumMax() : null,
                    stats != null ? stats.getHumAvg() : null));
            response.put("batteryLevel", buildStatObj(
                    stats != null ? stats.getBatMin() : null,
                    stats != null ? stats.getBatMax() : null,
                    stats != null ? stats.getBatAvg() : null));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting device stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> buildStatObj(Double min, Double max, Double avg) {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("min", min != null ? min : 0.0);
        stat.put("max", max != null ? max : 0.0);
        stat.put("avg", avg != null ? Math.round(avg * 100.0) / 100.0 : 0.0);
        return stat;
    }

    private String calculateStartTime(String period) {
        Instant now = Instant.now();
        Duration duration;

        if (period.endsWith("h") || period.endsWith("H")) {
            int hours = Integer.parseInt(period.replaceAll("[hH]", ""));
            duration = Duration.ofHours(hours);
        } else if (period.endsWith("d") || period.endsWith("D")) {
            int days = Integer.parseInt(period.replaceAll("[dD]", ""));
            duration = Duration.ofDays(days);
        } else {
            // Default to 24 hours
            duration = Duration.ofHours(24);
        }

        return now.minus(duration).toString();
    }
}

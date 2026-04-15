package com.iot.telemetry.controller;

import com.iot.telemetry.model.TelemetryReading;
import com.iot.telemetry.repository.DeviceRepository;
import com.iot.telemetry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for telemetry ingestion and query endpoints.
 *
 * Ingestion:
 * - POST /api/telemetry         → Ingest single reading (201)
 * - POST /api/telemetry/batch   → Bulk ingest readings (201)
 *
 * Queries (under /api/devices/{deviceId}/telemetry):
 * - GET  /api/devices/{deviceId}/telemetry/latest    → Latest reading (200/404)
 * - GET  /api/devices/{deviceId}/telemetry?start&end  → Time range query (200)
 * - GET  /api/devices/{deviceId}/telemetry/stats      → Aggregate stats (200)
 *
 * Location:
 * - GET  /api/locations/{location}/telemetry/latest  → Latest per device at location (200)
 */
@RestController
public class TelemetryController {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryController.class);

    private final TelemetryRepository telemetryRepository;
    private final DeviceRepository deviceRepository;

    public TelemetryController(TelemetryRepository telemetryRepository, DeviceRepository deviceRepository) {
        this.telemetryRepository = telemetryRepository;
        this.deviceRepository = deviceRepository;
    }

    // ----------------------------------------------------------
    // Telemetry Ingestion
    // ----------------------------------------------------------

    @PostMapping("/api/telemetry")
    public ResponseEntity<?> ingestTelemetry(@RequestBody TelemetryReading reading) {
        try {
            TelemetryReading created = telemetryRepository.ingest(reading);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Error ingesting telemetry: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/telemetry/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody List<TelemetryReading> readings) {
        try {
            int count = telemetryRepository.ingestBatch(readings);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("ingested", count));
        } catch (Exception e) {
            logger.error("Error batch ingesting telemetry: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ----------------------------------------------------------
    // Telemetry Queries
    // ----------------------------------------------------------

    @GetMapping("/api/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<?> getLatestReading(@PathVariable String deviceId) {
        Optional<TelemetryReading> reading = telemetryRepository.getLatestReading(deviceId);
        if (reading.isPresent()) {
            return ResponseEntity.ok(reading.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No readings found for device: " + deviceId));
    }

    @GetMapping("/api/devices/{deviceId}/telemetry")
    public ResponseEntity<?> getReadingsByTimeRange(
            @PathVariable String deviceId,
            @RequestParam String start,
            @RequestParam String end) {
        List<TelemetryReading> readings = telemetryRepository.getReadingsByTimeRange(deviceId, start, end);
        return ResponseEntity.ok(readings);
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/stats")
    public ResponseEntity<?> getDeviceStats(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24h") String period) {
        Map<String, Object> stats = telemetryRepository.getDeviceStats(deviceId, period);
        return ResponseEntity.ok(stats);
    }

    // ----------------------------------------------------------
    // Location-Level Queries
    // ----------------------------------------------------------

    @GetMapping("/api/locations/{location}/telemetry/latest")
    public ResponseEntity<?> getLocationSummary(@PathVariable String location) {
        List<String> deviceIds = deviceRepository.getDeviceIdsByLocation(location);
        List<TelemetryReading> latestReadings = telemetryRepository.getLatestReadingsForDevices(deviceIds);
        return ResponseEntity.ok(latestReadings);
    }
}

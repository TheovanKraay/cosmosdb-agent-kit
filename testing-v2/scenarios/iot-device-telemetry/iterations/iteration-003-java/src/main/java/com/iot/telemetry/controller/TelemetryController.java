package com.iot.telemetry.controller;

import com.azure.cosmos.CosmosException;
import com.iot.telemetry.model.Device;
import com.iot.telemetry.model.TelemetryReading;
import com.iot.telemetry.repository.DeviceRepository;
import com.iot.telemetry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
    public ResponseEntity<?> ingestTelemetry(@RequestBody Map<String, Object> body) {
        try {
            String deviceId = (String) body.get("deviceId");
            if (deviceId == null || deviceId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "deviceId is required"));
            }

            Number temperature = (Number) body.get("temperature");
            Number humidity = (Number) body.get("humidity");
            Number batteryLevel = (Number) body.get("batteryLevel");

            if (temperature == null || humidity == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "temperature and humidity are required"));
            }

            // Validate device exists
            try {
                deviceRepository.findById(deviceId).block();
            } catch (CosmosException e) {
                if (e.getStatusCode() == 404) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Device not found: " + deviceId));
                }
                throw e;
            }

            String timestamp = (String) body.get("timestamp");
            if (timestamp == null || timestamp.isEmpty()) {
                timestamp = Instant.now().toString();
            }

            double batteryValue = batteryLevel != null ? batteryLevel.doubleValue() : 0.0;

            String readingId = UUID.randomUUID().toString();
            TelemetryReading reading = new TelemetryReading(
                    readingId, deviceId, temperature.doubleValue(),
                    humidity.doubleValue(), batteryValue, timestamp
            );

            TelemetryReading created = telemetryRepository.createReading(reading).block();
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (CosmosException e) {
            logger.error("Cosmos error ingesting telemetry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error ingesting telemetry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/telemetry/batch")
    public ResponseEntity<?> ingestBatch(@RequestBody List<Map<String, Object>> readings) {
        try {
            int ingested = 0;
            for (Map<String, Object> body : readings) {
                String deviceId = (String) body.get("deviceId");
                Number temperature = (Number) body.get("temperature");
                Number humidity = (Number) body.get("humidity");
                Number batteryLevel = (Number) body.get("batteryLevel");

                if (deviceId == null || temperature == null || humidity == null) {
                    continue;
                }

                String timestamp = (String) body.get("timestamp");
                if (timestamp == null || timestamp.isEmpty()) {
                    timestamp = Instant.now().toString();
                }

                double batteryValue = batteryLevel != null ? batteryLevel.doubleValue() : 0.0;

                String readingId = UUID.randomUUID().toString();
                TelemetryReading reading = new TelemetryReading(
                        readingId, deviceId, temperature.doubleValue(),
                        humidity.doubleValue(), batteryValue, timestamp
                );

                // Rule 4.19: Fresh request options per createItem call
                telemetryRepository.createReading(reading).block();
                ingested++;
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ingested", ingested));
        } catch (Exception e) {
            logger.error("Error batch ingesting telemetry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/latest")
    public ResponseEntity<?> getLatestReading(@PathVariable String deviceId) {
        try {
            List<TelemetryReading> readings = telemetryRepository.findLatestByDeviceId(deviceId)
                    .collectList().block();

            if (readings == null || readings.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No readings found for device"));
            }

            return ResponseEntity.ok(readings.get(0));
        } catch (Exception e) {
            logger.error("Error getting latest reading", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry")
    public ResponseEntity<?> getReadingsByTimeRange(
            @PathVariable String deviceId,
            @RequestParam String start,
            @RequestParam String end) {
        try {
            List<TelemetryReading> readings = telemetryRepository
                    .findByDeviceIdAndTimeRange(deviceId, start, end)
                    .collectList().block();

            return ResponseEntity.ok(readings != null ? readings : Collections.emptyList());
        } catch (Exception e) {
            logger.error("Error querying readings by time range", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/devices/{deviceId}/telemetry/stats")
    public ResponseEntity<?> getDeviceStats(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "24h") String period) {
        try {
            // Parse period to determine time range
            Instant now = Instant.now();
            Instant start = parsePeriodStart(now, period);

            List<TelemetryReading> readings = telemetryRepository
                    .findByDeviceIdAndTimeRange(deviceId, start.toString(), now.toString())
                    .collectList().block();

            if (readings == null || readings.isEmpty()) {
                // Return zeroed stats when no readings found
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("deviceId", deviceId);
                stats.put("period", period);
                stats.put("temperature", Map.of("min", 0.0, "max", 0.0, "avg", 0.0));
                stats.put("humidity", Map.of("min", 0.0, "max", 0.0, "avg", 0.0));
                stats.put("batteryLevel", Map.of("min", 0.0, "max", 0.0, "avg", 0.0));
                return ResponseEntity.ok(stats);
            }

            DoubleSummaryStatistics tempStats = readings.stream()
                    .mapToDouble(TelemetryReading::getTemperature).summaryStatistics();
            DoubleSummaryStatistics humidityStats = readings.stream()
                    .mapToDouble(TelemetryReading::getHumidity).summaryStatistics();
            DoubleSummaryStatistics batteryStats = readings.stream()
                    .mapToDouble(TelemetryReading::getBatteryLevel).summaryStatistics();

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("deviceId", deviceId);
            stats.put("period", period);
            stats.put("temperature", Map.of(
                    "min", tempStats.getMin(),
                    "max", tempStats.getMax(),
                    "avg", Math.round(tempStats.getAverage() * 100.0) / 100.0
            ));
            stats.put("humidity", Map.of(
                    "min", humidityStats.getMin(),
                    "max", humidityStats.getMax(),
                    "avg", Math.round(humidityStats.getAverage() * 100.0) / 100.0
            ));
            stats.put("batteryLevel", Map.of(
                    "min", batteryStats.getMin(),
                    "max", batteryStats.getMax(),
                    "avg", Math.round(batteryStats.getAverage() * 100.0) / 100.0
            ));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error computing device stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/locations/{location}/telemetry/latest")
    public ResponseEntity<?> getLocationSummary(@PathVariable String location) {
        try {
            // Get all devices at this location
            List<Device> devices = deviceRepository.findByLocation(location)
                    .collectList().block();

            if (devices == null || devices.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Get latest reading for each device
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (Device device : devices) {
                List<TelemetryReading> readings = telemetryRepository
                        .findLatestByDeviceId(device.getDeviceId())
                        .collectList().block();

                if (readings != null && !readings.isEmpty()) {
                    TelemetryReading latest = readings.get(0);
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
            logger.error("Error getting location summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Instant parsePeriodStart(Instant now, String period) {
        if (period == null || period.isEmpty()) {
            return now.minus(24, ChronoUnit.HOURS);
        }

        try {
            String value = period.substring(0, period.length() - 1);
            char unit = period.charAt(period.length() - 1);
            long amount = Long.parseLong(value);

            return switch (unit) {
                case 'h' -> now.minus(amount, ChronoUnit.HOURS);
                case 'd' -> now.minus(amount, ChronoUnit.DAYS);
                case 'm' -> now.minus(amount, ChronoUnit.MINUTES);
                default -> now.minus(24, ChronoUnit.HOURS);
            };
        } catch (Exception e) {
            return now.minus(24, ChronoUnit.HOURS);
        }
    }
}

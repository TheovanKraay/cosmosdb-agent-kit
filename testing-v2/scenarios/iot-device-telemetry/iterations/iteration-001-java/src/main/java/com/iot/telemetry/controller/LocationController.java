package com.iot.telemetry.controller;

import com.iot.telemetry.model.Device;
import com.iot.telemetry.model.TelemetryReading;
import com.iot.telemetry.repository.DeviceRepository;
import com.iot.telemetry.repository.TelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for location-level telemetry queries.
 *
 * Endpoint:
 * - GET /api/locations/{location}/telemetry/latest → Latest reading per device at location (200)
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private static final Logger log = LoggerFactory.getLogger(LocationController.class);
    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;

    public LocationController(DeviceRepository deviceRepository,
                              TelemetryRepository telemetryRepository) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @GetMapping("/{location}/telemetry/latest")
    public ResponseEntity<?> getLocationSummary(@PathVariable String location) {
        try {
            // Step 1: Get all devices at this location
            List<Device> devices = deviceRepository.getDevicesByLocation(location)
                    .collectList().block();

            if (devices == null || devices.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Step 2: Get latest reading for each device
            List<String> deviceIds = devices.stream()
                    .map(Device::getDeviceId)
                    .toList();

            List<TelemetryReading> latestReadings = telemetryRepository
                    .getLatestReadingsByDeviceIds(deviceIds)
                    .collectList().block();

            if (latestReadings == null) {
                latestReadings = Collections.emptyList();
            }

            // Step 3: Build response with required fields only
            List<Map<String, Object>> response = new ArrayList<>();
            for (TelemetryReading reading : latestReadings) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("deviceId", reading.getDeviceId());
                entry.put("temperature", reading.getTemperature());
                entry.put("humidity", reading.getHumidity());
                entry.put("batteryLevel", reading.getBatteryLevel());
                entry.put("timestamp", reading.getTimestamp());
                response.add(entry);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting location summary", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

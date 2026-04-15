package com.iot.telemetry.controller;

import com.azure.cosmos.CosmosException;
import com.iot.telemetry.model.Device;
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
 * REST controller for device management endpoints.
 *
 * Endpoints:
 * - POST   /api/devices           → Register a new device (201)
 * - GET    /api/devices/{deviceId} → Get device by ID (200/404)
 * - PATCH  /api/devices/{deviceId} → Update device metadata (200/404)
 * - DELETE /api/devices/{deviceId} → Delete device + telemetry (204/404)
 * - GET    /api/devices?location=X → Get devices by location (200)
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;

    public DeviceController(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @PostMapping
    public ResponseEntity<?> registerDevice(@RequestBody Device device) {
        try {
            Device created = deviceRepository.createDevice(device);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Device already exists: " + device.getDeviceId()));
            }
            logger.error("Error creating device: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getDevice(@PathVariable String deviceId) {
        Optional<Device> device = deviceRepository.getDevice(deviceId);
        if (device.isPresent()) {
            return ResponseEntity.ok(device.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Device not found: " + deviceId));
    }

    @GetMapping
    public ResponseEntity<List<Device>> getDevicesByLocation(@RequestParam String location) {
        List<Device> devices = deviceRepository.getDevicesByLocation(location);
        return ResponseEntity.ok(devices);
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<?> updateDevice(@PathVariable String deviceId, @RequestBody Device updates) {
        Optional<Device> updated = deviceRepository.updateDevice(deviceId, updates);
        if (updated.isPresent()) {
            return ResponseEntity.ok(updated.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Device not found: " + deviceId));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> deleteDevice(@PathVariable String deviceId) {
        boolean deleted = deviceRepository.deleteDevice(deviceId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Device not found: " + deviceId));
        }
        // Also delete all telemetry data for this device
        try {
            telemetryRepository.deleteReadingsForDevice(deviceId);
        } catch (Exception e) {
            logger.warn("Error cleaning up telemetry for device '{}': {}", deviceId, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}

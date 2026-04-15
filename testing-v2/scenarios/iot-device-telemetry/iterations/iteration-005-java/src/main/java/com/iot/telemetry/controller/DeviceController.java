package com.iot.telemetry.controller;

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
    public ResponseEntity<?> registerDevice(@RequestBody(required = false) Device device) {
        if (device == null || device.getDeviceId() == null || device.getDeviceId().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "deviceId is required"));
        }
        if (device.getName() == null || device.getName().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "name is required"));
        }
        if (device.getLocation() == null || device.getLocation().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "location is required"));
        }
        if (device.getDeviceType() == null || device.getDeviceType().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "deviceType is required"));
        }
        try {
            Device created = deviceRepository.createDevice(device);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Error registering device: {}", e.getMessage(), e);
            if (e.getMessage() != null && (e.getMessage().contains("409") || e.getMessage().contains("Conflict"))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Device already exists: " + device.getDeviceId()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to register device: " + e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getDevice(@PathVariable String deviceId) {
        try {
            Device device = deviceRepository.getDevice(deviceId);
            if (device == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found: " + deviceId));
            }
            return ResponseEntity.ok(device);
        } catch (Exception e) {
            logger.error("Error getting device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get device: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getDevicesByLocation(@RequestParam String location) {
        try {
            List<Device> devices = deviceRepository.getDevicesByLocation(location);
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            logger.error("Error querying devices by location {}: {}", location, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to query devices: " + e.getMessage()));
        }
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<?> updateDevice(@PathVariable String deviceId, @RequestBody Device updates) {
        try {
            Device updated = deviceRepository.updateDevice(deviceId, updates);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found: " + deviceId));
            }
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Error updating device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update device: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> deleteDevice(@PathVariable String deviceId) {
        try {
            boolean deleted = deviceRepository.deleteDevice(deviceId);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found: " + deviceId));
            }
            // Also delete telemetry data for this device
            try {
                telemetryRepository.deleteReadingsForDevice(deviceId);
            } catch (Exception e) {
                logger.warn("Failed to delete telemetry for device {}: {}", deviceId, e.getMessage());
            }
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting device {}: {}", deviceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete device: " + e.getMessage()));
        }
    }
}

package com.iot.telemetry.controller;

import com.azure.cosmos.CosmosException;
import com.iot.telemetry.model.Device;
import com.iot.telemetry.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Device management endpoints.
 *
 * Endpoints:
 * - POST   /api/devices                → Register device (201)
 * - GET    /api/devices/{deviceId}      → Get device (200/404)
 * - GET    /api/devices?location=X      → List devices at location (200)
 * - PATCH  /api/devices/{deviceId}      → Update device (200/404)
 * - DELETE /api/devices/{deviceId}      → Delete device (204/404)
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);
    private final DeviceRepository deviceRepository;
    private final com.iot.telemetry.repository.TelemetryRepository telemetryRepository;

    public DeviceController(DeviceRepository deviceRepository,
                            com.iot.telemetry.repository.TelemetryRepository telemetryRepository) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @PostMapping
    public ResponseEntity<?> registerDevice(@RequestBody Device device) {
        try {
            // Validate required fields
            if (device.getDeviceId() == null || device.getDeviceId().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "deviceId is required"));
            }
            if (device.getName() == null || device.getName().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "name is required"));
            }
            if (device.getLocation() == null || device.getLocation().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "location is required"));
            }
            if (device.getDeviceType() == null || device.getDeviceType().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "deviceType is required"));
            }

            device.setId(device.getDeviceId());
            Device created = deviceRepository.createDevice(device).block();
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Device already exists"));
            }
            log.error("Error creating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getDevice(@PathVariable String deviceId) {
        try {
            Device device = deviceRepository.getDevice(deviceId).block();
            if (device == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }
            return ResponseEntity.ok(device);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }
            log.error("Error getting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getDevicesByLocation(@RequestParam(required = false) String location) {
        try {
            if (location == null || location.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            List<Device> devices = deviceRepository.getDevicesByLocation(location)
                    .collectList().block();
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            log.error("Error querying devices by location", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<?> updateDevice(@PathVariable String deviceId,
                                          @RequestBody Map<String, Object> updates) {
        try {
            // Read existing device first
            Device existing = deviceRepository.getDevice(deviceId).block();
            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }

            // Apply partial updates
            if (updates.containsKey("name")) {
                existing.setName((String) updates.get("name"));
            }
            if (updates.containsKey("location")) {
                existing.setLocation((String) updates.get("location"));
            }
            if (updates.containsKey("deviceType")) {
                existing.setDeviceType((String) updates.get("deviceType"));
            }

            Device updated = deviceRepository.updateDevice(existing).block();
            return ResponseEntity.ok(updated);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }
            log.error("Error updating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> deleteDevice(@PathVariable String deviceId) {
        try {
            // Check if device exists first
            deviceRepository.getDevice(deviceId).block();

            // Delete telemetry data for this device
            telemetryRepository.deleteReadingsForDevice(deviceId).block();

            // Delete the device
            deviceRepository.deleteDevice(deviceId).block();
            return ResponseEntity.noContent().build();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }
            log.error("Error deleting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

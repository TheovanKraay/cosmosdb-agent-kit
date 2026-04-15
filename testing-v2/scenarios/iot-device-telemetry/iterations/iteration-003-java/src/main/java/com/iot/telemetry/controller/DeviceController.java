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
    public ResponseEntity<?> registerDevice(@RequestBody Map<String, Object> body) {
        try {
            String deviceId = (String) body.get("deviceId");
            String name = (String) body.get("name");
            String location = (String) body.get("location");
            String deviceType = (String) body.get("deviceType");

            if (deviceId == null || name == null || location == null || deviceType == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            Device device = new Device(deviceId, name, location, deviceType);
            Device created = deviceRepository.createDevice(device).block();
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Device already exists"));
            }
            logger.error("Error registering device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error registering device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getDevice(@PathVariable String deviceId) {
        try {
            Device device = deviceRepository.findById(deviceId).block();
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
            logger.error("Error getting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<?> updateDevice(@PathVariable String deviceId, @RequestBody Map<String, Object> body) {
        try {
            Device device = deviceRepository.findById(deviceId).block();
            if (device == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }

            if (body.containsKey("name")) {
                device.setName((String) body.get("name"));
            }
            if (body.containsKey("location")) {
                device.setLocation((String) body.get("location"));
            }
            if (body.containsKey("deviceType")) {
                device.setDeviceType((String) body.get("deviceType"));
            }

            Device updated = deviceRepository.updateDevice(device).block();
            return ResponseEntity.ok(updated);
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }
            logger.error("Error updating device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> deleteDevice(@PathVariable String deviceId) {
        try {
            // Verify device exists first
            deviceRepository.findById(deviceId).block();

            // Delete all telemetry for this device
            telemetryRepository.deleteAllByDeviceId(deviceId).block();

            // Delete the device
            deviceRepository.deleteDevice(deviceId).block();
            return ResponseEntity.noContent().build();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found"));
            }
            logger.error("Error deleting device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getDevicesByLocation(@RequestParam String location) {
        try {
            List<Device> devices = deviceRepository.findByLocation(location)
                    .collectList().block();
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            logger.error("Error querying devices by location", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

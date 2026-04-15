package com.iot.telemetry.controller;

import com.iot.telemetry.model.Device;
import com.iot.telemetry.repository.DeviceRepository;
import com.iot.telemetry.repository.TelemetryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;

    public DeviceController(DeviceRepository deviceRepository,
                            TelemetryRepository telemetryRepository) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerDevice(@RequestBody Device device) {
        Device created = deviceRepository.createDevice(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDeviceResponse(created));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<Map<String, Object>> getDevice(@PathVariable String deviceId) {
        Device device = deviceRepository.getDevice(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDeviceResponse(device));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getDevicesByLocation(
            @RequestParam String location) {
        List<Device> devices = deviceRepository.getDevicesByLocation(location);
        List<Map<String, Object>> response = devices.stream()
                .map(this::toDeviceResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<Map<String, Object>> updateDevice(
            @PathVariable String deviceId,
            @RequestBody Device updates) {
        Device updated = deviceRepository.updateDevice(deviceId, updates);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDeviceResponse(updated));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable String deviceId) {
        boolean deleted = deviceRepository.deleteDevice(deviceId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        // Also delete all telemetry data for this device
        telemetryRepository.deleteReadingsForDevice(deviceId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDeviceResponse(Device device) {
        return Map.of(
                "deviceId", device.getDeviceId(),
                "name", device.getName(),
                "location", device.getLocation(),
                "deviceType", device.getDeviceType()
        );
    }
}

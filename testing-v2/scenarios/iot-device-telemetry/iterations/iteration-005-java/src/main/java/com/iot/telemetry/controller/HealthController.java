package com.iot.telemetry.controller;

import com.iot.telemetry.config.CosmosConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final CosmosConfig cosmosConfig;

    public HealthController(CosmosConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        if (cosmosConfig.isReady()) {
            return ResponseEntity.ok(Map.of("status", "healthy"));
        }
        return ResponseEntity.status(503).body(Map.of("status", "initializing"));
    }
}

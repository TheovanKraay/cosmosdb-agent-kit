package com.iot.telemetry.controller;

import com.iot.telemetry.config.CosmosDbConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint.
 * Returns 503 until Cosmos DB containers are initialized,
 * then returns 200 to signal readiness.
 */
@RestController
public class HealthController {

    private final CosmosDbConfig cosmosDbConfig;

    public HealthController(CosmosDbConfig cosmosDbConfig) {
        this.cosmosDbConfig = cosmosDbConfig;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (cosmosDbConfig.isReady()) {
            return ResponseEntity.ok(Map.of("status", "healthy"));
        }
        return ResponseEntity.status(503).body(Map.of("status", "initializing"));
    }
}

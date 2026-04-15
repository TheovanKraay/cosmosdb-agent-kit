package com.ecommerce.orderapi.controller;

import com.ecommerce.orderapi.config.CosmosDbConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health endpoint that gates on Cosmos DB readiness.
 * Returns 503 until the background warmup has successfully initialized the container.
 */
@RestController
public class HealthController {

    private final CosmosDbConfiguration cosmosConfig;

    public HealthController(CosmosDbConfiguration cosmosConfig) {
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

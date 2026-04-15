package com.ecommerce.order.controller;

import com.ecommerce.order.config.CosmosDbConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health endpoint. Returns 200 only when Cosmos DB is initialized.
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

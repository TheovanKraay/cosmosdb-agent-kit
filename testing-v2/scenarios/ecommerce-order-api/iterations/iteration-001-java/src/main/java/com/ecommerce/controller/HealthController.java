package com.ecommerce.controller;

import com.ecommerce.config.CosmosDbConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

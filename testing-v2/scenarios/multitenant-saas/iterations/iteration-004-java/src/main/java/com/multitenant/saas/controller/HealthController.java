package com.multitenant.saas.controller;

import com.multitenant.saas.config.CosmosConfig;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<Map<String, String>> health() {
        if (!cosmosConfig.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "initializing"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

package com.multitenant.controller;

import com.multitenant.model.AnalyticsResponse;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/analytics")
public class AnalyticsController {

    private final MultitenantRepository repository;

    public AnalyticsController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String tenantId) {
        AnalyticsResponse analytics = repository.getAnalytics(tenantId);
        return ResponseEntity.ok(analytics);
    }
}

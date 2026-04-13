package com.multitenant.controller;

import com.multitenant.model.Tenant;
import com.multitenant.model.TenantAnalytics;
import com.multitenant.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final MultiTenantRepository repository;

    public TenantController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Tenant> createTenant(@RequestBody Tenant tenant) {
        Tenant created = repository.createTenant(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<Tenant> getTenant(@PathVariable String tenantId) {
        Tenant tenant = repository.getTenant(tenantId);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/{tenantId}/analytics")
    public ResponseEntity<TenantAnalytics> getAnalytics(@PathVariable String tenantId) {
        TenantAnalytics analytics = repository.getAnalytics(tenantId);
        return ResponseEntity.ok(analytics);
    }
}

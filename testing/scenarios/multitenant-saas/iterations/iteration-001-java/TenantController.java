package com.example.multitenentsaas.controller;

import com.example.multitenentsaas.model.Tenant;
import com.example.multitenentsaas.dto.TenantAnalytics;
import com.example.multitenentsaas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for tenant management.
 * All operations are scoped to a specific tenant via path parameter.
 */
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

    @PutMapping("/{tenantId}")
    public ResponseEntity<Tenant> updateTenant(@PathVariable String tenantId, @RequestBody Tenant tenant) {
        tenant.setTenantId(tenantId);
        Tenant updated = repository.updateTenant(tenant);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> deleteTenant(@PathVariable String tenantId) {
        Tenant existing = repository.getTenant(tenantId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteTenant(tenantId, existing.getId());
        return ResponseEntity.noContent().build();
    }

    /** Tenant analytics endpoint using denormalized project task counts */
    @GetMapping("/{tenantId}/analytics")
    public ResponseEntity<TenantAnalytics> getTenantAnalytics(@PathVariable String tenantId) {
        TenantAnalytics analytics = repository.getTenantAnalytics(tenantId);
        return ResponseEntity.ok(analytics);
    }
}

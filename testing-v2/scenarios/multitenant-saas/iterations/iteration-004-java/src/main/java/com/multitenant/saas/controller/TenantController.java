package com.multitenant.saas.controller;

import com.multitenant.saas.model.Tenant;
import com.multitenant.saas.model.TenantAnalytics;
import com.multitenant.saas.model.Task;
import com.multitenant.saas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{tenantId}/tasks")
    public ResponseEntity<List<Task>> getTasksByStatus(
            @PathVariable String tenantId,
            @RequestParam String status) {
        List<Task> tasks = repository.getTasksByStatus(tenantId, status);
        return ResponseEntity.ok(tasks);
    }
}

package com.multitenant.controller;

import com.multitenant.model.Tenant;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final MultitenantRepository repository;

    public TenantController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Tenant> createTenant(@RequestBody Map<String, String> body) {
        Tenant tenant = new Tenant();
        String tenantId = UUID.randomUUID().toString();
        tenant.setId(tenantId);
        tenant.setTenantId(tenantId);
        tenant.setName(body.get("name"));
        tenant.setPlan(body.get("plan"));
        tenant.setCreatedAt(Instant.now().toString());

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
}

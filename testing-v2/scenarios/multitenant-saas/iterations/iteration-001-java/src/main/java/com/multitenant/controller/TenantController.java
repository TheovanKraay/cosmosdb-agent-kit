package com.multitenant.controller;

import com.multitenant.model.Tenant;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final MultitenantRepository repository;

    public TenantController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Tenant> createTenant(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String plan = body.get("plan");
        Tenant tenant = repository.createTenant(name, plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<Tenant> getTenant(@PathVariable String tenantId) {
        Optional<Tenant> tenant = repository.getTenant(tenantId);
        return tenant.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

package com.example.multitenentsaas.controller;

import com.example.multitenentsaas.model.User;
import com.example.multitenentsaas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user management within a tenant.
 * Tenant isolation enforced via tenantId path parameter.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
public class UserController {

    private final MultiTenantRepository repository;

    public UserController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@PathVariable String tenantId, @RequestBody User user) {
        User created = repository.createUser(tenantId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<User>> getUsers(@PathVariable String tenantId) {
        List<User> users = repository.getUsersByTenant(tenantId);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String tenantId, @PathVariable String userId) {
        User user = repository.getUser(tenantId, userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable String tenantId, @PathVariable String userId,
                                           @RequestBody User user) {
        user.setId(userId);
        User updated = repository.updateUser(tenantId, user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String tenantId, @PathVariable String userId) {
        repository.deleteUser(tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}

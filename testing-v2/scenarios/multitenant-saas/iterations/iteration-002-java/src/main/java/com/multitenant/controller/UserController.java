package com.multitenant.controller;

import com.multitenant.model.User;
import com.multitenant.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
public class UserController {

    private final MultiTenantRepository repository;

    public UserController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<User> createUser(
            @PathVariable String tenantId,
            @RequestBody User user) {
        User created = repository.createUser(tenantId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers(@PathVariable String tenantId) {
        List<User> users = repository.listUsers(tenantId);
        return ResponseEntity.ok(users);
    }
}

package com.multitenant.saas.controller;

import com.multitenant.saas.model.User;
import com.multitenant.saas.model.Task;
import com.multitenant.saas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{userId}/tasks")
    public ResponseEntity<List<Task>> getUserTasks(
            @PathVariable String tenantId,
            @PathVariable String userId) {
        List<Task> tasks = repository.getUserTasks(tenantId, userId);
        return ResponseEntity.ok(tasks);
    }
}

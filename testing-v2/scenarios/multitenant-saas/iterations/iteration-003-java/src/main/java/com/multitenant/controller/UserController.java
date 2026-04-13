package com.multitenant.controller;

import com.multitenant.model.User;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
public class UserController {

    private final MultitenantRepository repository;

    public UserController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<User> createUser(
            @PathVariable String tenantId,
            @RequestBody Map<String, String> body) {
        User user = new User();
        String userId = UUID.randomUUID().toString();
        user.setId(userId);
        user.setUserId(userId);
        user.setTenantId(tenantId);
        user.setName(body.get("name"));
        user.setEmail(body.get("email"));
        user.setRole(body.get("role"));

        User created = repository.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers(@PathVariable String tenantId) {
        List<User> users = repository.listUsers(tenantId);
        return ResponseEntity.ok(users);
    }
}

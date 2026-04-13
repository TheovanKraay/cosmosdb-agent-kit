package com.multitenant.controller;

import com.multitenant.model.User;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
public class UserController {

    private final MultitenantRepository repository;

    public UserController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@PathVariable String tenantId,
                                           @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String email = body.get("email");
        String role = body.get("role");
        User user = repository.createUser(tenantId, name, email, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers(@PathVariable String tenantId) {
        List<User> users = repository.listUsers(tenantId);
        return ResponseEntity.ok(users);
    }
}

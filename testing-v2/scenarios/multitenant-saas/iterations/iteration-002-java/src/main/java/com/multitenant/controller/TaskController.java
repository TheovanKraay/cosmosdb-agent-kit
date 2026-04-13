package com.multitenant.controller;

import com.multitenant.model.Task;
import com.multitenant.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TaskController {

    private final MultiTenantRepository repository;

    public TaskController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Task> createTask(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody Task task) {
        Task created = repository.createTask(tenantId, projectId, task);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<List<Task>> listTasksByProject(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        List<Task> tasks = repository.listTasksByProject(tenantId, projectId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/users/{userId}/tasks")
    public ResponseEntity<List<Task>> listTasksByUser(
            @PathVariable String tenantId,
            @PathVariable String userId) {
        List<Task> tasks = repository.listTasksByUser(tenantId, userId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> listTasksByStatus(
            @PathVariable String tenantId,
            @RequestParam String status) {
        List<Task> tasks = repository.listTasksByStatus(tenantId, status);
        return ResponseEntity.ok(tasks);
    }
}

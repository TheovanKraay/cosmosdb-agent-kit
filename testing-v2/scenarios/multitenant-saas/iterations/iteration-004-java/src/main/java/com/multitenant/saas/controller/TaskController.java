package com.multitenant.saas.controller;

import com.multitenant.saas.model.Task;
import com.multitenant.saas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/tasks")
public class TaskController {

    private final MultiTenantRepository repository;

    public TaskController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Task> createTask(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody Task task) {
        Task created = repository.createTask(tenantId, projectId, task);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Task>> listProjectTasks(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        List<Task> tasks = repository.listProjectTasks(tenantId, projectId);
        return ResponseEntity.ok(tasks);
    }
}

package com.multitenant.controller;

import com.multitenant.model.Task;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TaskController {

    private final MultitenantRepository repository;

    public TaskController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Task> createTask(@PathVariable String tenantId,
                                            @PathVariable String projectId,
                                            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        String assigneeId = body.get("assigneeId");
        String priority = body.get("priority");
        String status = body.getOrDefault("status", null);
        Task task = repository.createTask(tenantId, projectId, title, assigneeId, priority, status);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<List<Task>> listProjectTasks(@PathVariable String tenantId,
                                                        @PathVariable String projectId) {
        List<Task> tasks = repository.listProjectTasks(tenantId, projectId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/users/{userId}/tasks")
    public ResponseEntity<List<Task>> listUserTasks(@PathVariable String tenantId,
                                                     @PathVariable String userId) {
        List<Task> tasks = repository.listUserTasks(tenantId, userId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> listTasksByStatus(@PathVariable String tenantId,
                                                         @RequestParam String status) {
        List<Task> tasks = repository.listTasksByStatus(tenantId, status);
        return ResponseEntity.ok(tasks);
    }
}

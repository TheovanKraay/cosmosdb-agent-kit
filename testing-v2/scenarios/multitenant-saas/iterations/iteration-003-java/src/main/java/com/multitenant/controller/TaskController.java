package com.multitenant.controller;

import com.multitenant.model.Task;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TaskController {

    private final MultitenantRepository repository;

    public TaskController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Task> createTask(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody Map<String, String> body) {
        Task task = new Task();
        String taskId = UUID.randomUUID().toString();
        task.setId(taskId);
        task.setTaskId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setTitle(body.get("title"));
        task.setAssigneeId(body.get("assigneeId"));
        task.setPriority(body.get("priority"));
        task.setStatus(body.getOrDefault("status", "todo"));
        task.setCreatedAt(Instant.now().toString());

        Task created = repository.createTask(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<List<Task>> listProjectTasks(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        List<Task> tasks = repository.listProjectTasks(tenantId, projectId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/users/{userId}/tasks")
    public ResponseEntity<List<Task>> listUserTasks(
            @PathVariable String tenantId,
            @PathVariable String userId) {
        List<Task> tasks = repository.listUserTasks(tenantId, userId);
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

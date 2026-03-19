package com.example.multitenentsaas.controller;

import com.example.multitenentsaas.model.Task;
import com.example.multitenentsaas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for task management within a tenant's project.
 * Tenant isolation enforced via tenantId path parameter.
 *
 * Tasks use the full 3-level hierarchical partition key (tenantId/type/projectId)
 * for efficient single-partition queries (Rule 3.1).
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TaskController {

    private final MultiTenantRepository repository;

    public TaskController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    /** Create a task within a project */
    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Task> createTask(@PathVariable String tenantId, @PathVariable String projectId,
                                           @RequestBody Task task) {
        Task created = repository.createTask(tenantId, projectId, task);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** List all tasks in a project (single-partition query) */
    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<List<Task>> getTasksByProject(@PathVariable String tenantId,
                                                         @PathVariable String projectId) {
        List<Task> tasks = repository.getTasksByProject(tenantId, projectId);
        return ResponseEntity.ok(tasks);
    }

    /** Get a specific task */
    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<Task> getTask(@PathVariable String tenantId, @PathVariable String projectId,
                                        @PathVariable String taskId) {
        Task task = repository.getTask(tenantId, projectId, taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    /** Update a task (status change, reassignment, etc.) */
    @PutMapping("/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<Task> updateTask(@PathVariable String tenantId, @PathVariable String projectId,
                                            @PathVariable String taskId, @RequestBody Task task) {
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        Task updated = repository.updateTask(tenantId, projectId, task);
        return ResponseEntity.ok(updated);
    }

    /** Delete a task */
    @DeleteMapping("/projects/{projectId}/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable String tenantId, @PathVariable String projectId,
                                            @PathVariable String taskId) {
        repository.deleteTask(tenantId, projectId, taskId);
        return ResponseEntity.noContent().build();
    }

    /** Add a comment to a task (Rule 1.3: embedded comments) */
    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments")
    public ResponseEntity<Task> addComment(@PathVariable String tenantId, @PathVariable String projectId,
                                            @PathVariable String taskId, @RequestBody Task.Comment comment) {
        Task updated = repository.addComment(tenantId, projectId, taskId, comment);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    /** Get tasks assigned to a specific user across all projects in the tenant */
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getTasksByAssignee(@PathVariable String tenantId,
                                                          @RequestParam(required = false) String assigneeId,
                                                          @RequestParam(required = false) String status) {
        if (assigneeId != null) {
            List<Task> tasks = repository.getTasksByAssignee(tenantId, assigneeId);
            return ResponseEntity.ok(tasks);
        } else if (status != null) {
            List<Task> tasks = repository.getTasksByStatus(tenantId, status);
            return ResponseEntity.ok(tasks);
        }
        return ResponseEntity.badRequest().build();
    }
}

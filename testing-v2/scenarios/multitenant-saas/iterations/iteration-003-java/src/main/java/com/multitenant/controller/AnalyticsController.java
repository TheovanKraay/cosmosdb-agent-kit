package com.multitenant.controller;

import com.multitenant.model.Task;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants/{tenantId}/analytics")
public class AnalyticsController {

    private final MultitenantRepository repository;

    public AnalyticsController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable String tenantId) {
        int totalUsers = repository.countByType(tenantId, "user");
        int totalProjects = repository.countByType(tenantId, "project");

        List<Task> allTasks = repository.listAllTasks(tenantId);
        int totalTasks = allTasks.size();

        Map<String, Integer> tasksByStatus = new HashMap<>();
        tasksByStatus.put("todo", 0);
        tasksByStatus.put("in-progress", 0);
        tasksByStatus.put("done", 0);
        tasksByStatus.put("blocked", 0);

        Map<String, Integer> tasksByPriority = new HashMap<>();
        tasksByPriority.put("low", 0);
        tasksByPriority.put("medium", 0);
        tasksByPriority.put("high", 0);
        tasksByPriority.put("critical", 0);

        for (Task task : allTasks) {
            String status = task.getStatus();
            if (status != null) {
                tasksByStatus.merge(status, 1, Integer::sum);
            }
            String priority = task.getPriority();
            if (priority != null) {
                tasksByPriority.merge(priority, 1, Integer::sum);
            }
        }

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("tenantId", tenantId);
        analytics.put("totalUsers", totalUsers);
        analytics.put("totalProjects", totalProjects);
        analytics.put("totalTasks", totalTasks);
        analytics.put("tasksByStatus", tasksByStatus);
        analytics.put("tasksByPriority", tasksByPriority);

        return ResponseEntity.ok(analytics);
    }
}

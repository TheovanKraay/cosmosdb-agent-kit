package com.multitenant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.multitenant.repository.CosmosRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants/{tenantId}/analytics")
public class AnalyticsController {

    private final CosmosRepository cosmosRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsController(CosmosRepository cosmosRepository) {
        this.cosmosRepository = cosmosRepository;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public ResponseEntity<JsonNode> getAnalytics(@PathVariable String tenantId) {
        int totalUsers = cosmosRepository.countByType(tenantId, "user");
        int totalProjects = cosmosRepository.countByType(tenantId, "project");
        int totalTasks = cosmosRepository.countByType(tenantId, "task");

        int todoCount = cosmosRepository.countByTypeAndField(tenantId, "task", "status", "todo");
        int inProgressCount = cosmosRepository.countByTypeAndField(tenantId, "task", "status", "in-progress");
        int doneCount = cosmosRepository.countByTypeAndField(tenantId, "task", "status", "done");
        int blockedCount = cosmosRepository.countByTypeAndField(tenantId, "task", "status", "blocked");

        int lowCount = cosmosRepository.countByTypeAndField(tenantId, "task", "priority", "low");
        int mediumCount = cosmosRepository.countByTypeAndField(tenantId, "task", "priority", "medium");
        int highCount = cosmosRepository.countByTypeAndField(tenantId, "task", "priority", "high");
        int criticalCount = cosmosRepository.countByTypeAndField(tenantId, "task", "priority", "critical");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("tenantId", tenantId);
        response.put("totalUsers", totalUsers);
        response.put("totalProjects", totalProjects);
        response.put("totalTasks", totalTasks);

        ObjectNode tasksByStatus = objectMapper.createObjectNode();
        tasksByStatus.put("todo", todoCount);
        tasksByStatus.put("in-progress", inProgressCount);
        tasksByStatus.put("done", doneCount);
        tasksByStatus.put("blocked", blockedCount);
        response.set("tasksByStatus", tasksByStatus);

        ObjectNode tasksByPriority = objectMapper.createObjectNode();
        tasksByPriority.put("low", lowCount);
        tasksByPriority.put("medium", mediumCount);
        tasksByPriority.put("high", highCount);
        tasksByPriority.put("critical", criticalCount);
        response.set("tasksByPriority", tasksByPriority);

        return ResponseEntity.ok(response);
    }
}

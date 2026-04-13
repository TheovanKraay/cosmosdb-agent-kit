package com.multitenant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.multitenant.repository.CosmosRepository;
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

    private final CosmosRepository cosmosRepository;
    private final ObjectMapper objectMapper;

    public TaskController(CosmosRepository cosmosRepository) {
        this.cosmosRepository = cosmosRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<JsonNode> createTask(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody Map<String, Object> body) {
        String taskId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        String status = body.containsKey("status") ? (String) body.get("status") : "todo";

        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("id", taskId);
        doc.put("tenantId", tenantId);
        doc.put("type", "task");
        doc.put("schemaVersion", 1);
        doc.put("taskId", taskId);
        doc.put("projectId", projectId);
        doc.put("title", (String) body.get("title"));
        doc.put("assigneeId", (String) body.get("assigneeId"));
        doc.put("priority", (String) body.get("priority"));
        doc.put("status", status);
        doc.put("createdAt", now);

        cosmosRepository.createItem(doc);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("tenantId", tenantId);
        response.put("projectId", projectId);
        response.put("title", (String) body.get("title"));
        response.put("assigneeId", (String) body.get("assigneeId"));
        response.put("priority", (String) body.get("priority"));
        response.put("status", status);
        response.put("createdAt", now);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<JsonNode> listProjectTasks(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        List<JsonNode> docs = cosmosRepository.queryTasksByProject(tenantId, projectId);
        return ResponseEntity.ok(buildTaskArray(docs));
    }

    @GetMapping("/users/{userId}/tasks")
    public ResponseEntity<JsonNode> getUserTasks(
            @PathVariable String tenantId,
            @PathVariable String userId) {
        List<JsonNode> docs = cosmosRepository.queryTasksByAssignee(tenantId, userId);
        return ResponseEntity.ok(buildTaskArray(docs));
    }

    @GetMapping("/tasks")
    public ResponseEntity<JsonNode> queryTasksByStatus(
            @PathVariable String tenantId,
            @RequestParam String status) {
        List<JsonNode> docs = cosmosRepository.queryTasksByStatus(tenantId, status);
        return ResponseEntity.ok(buildTaskArray(docs));
    }

    private ArrayNode buildTaskArray(List<JsonNode> docs) {
        ArrayNode response = objectMapper.createArrayNode();
        for (JsonNode doc : docs) {
            ObjectNode task = objectMapper.createObjectNode();
            task.put("taskId", doc.get("taskId").asText());
            task.put("tenantId", doc.get("tenantId").asText());
            task.put("projectId", doc.get("projectId").asText());
            task.put("title", doc.get("title").asText());
            task.put("assigneeId", doc.get("assigneeId").asText());
            task.put("priority", doc.get("priority").asText());
            task.put("status", doc.get("status").asText());
            if (doc.has("createdAt")) {
                task.put("createdAt", doc.get("createdAt").asText());
            }
            response.add(task);
        }
        return response;
    }
}

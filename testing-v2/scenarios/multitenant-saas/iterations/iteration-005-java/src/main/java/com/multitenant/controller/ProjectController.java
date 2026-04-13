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
@RequestMapping("/api/tenants/{tenantId}/projects")
public class ProjectController {

    private final CosmosRepository cosmosRepository;
    private final ObjectMapper objectMapper;

    public ProjectController(CosmosRepository cosmosRepository) {
        this.cosmosRepository = cosmosRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping
    public ResponseEntity<JsonNode> createProject(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> body) {
        String projectId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("id", projectId);
        doc.put("tenantId", tenantId);
        doc.put("type", "project");
        doc.put("schemaVersion", 1);
        doc.put("projectId", projectId);
        doc.put("name", (String) body.get("name"));
        doc.put("description", body.containsKey("description") ? (String) body.get("description") : "");
        doc.put("createdAt", now);

        cosmosRepository.createItem(doc);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("projectId", projectId);
        response.put("tenantId", tenantId);
        response.put("name", (String) body.get("name"));
        response.put("description", body.containsKey("description") ? (String) body.get("description") : "");
        response.put("createdAt", now);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<JsonNode> listProjects(@PathVariable String tenantId) {
        List<JsonNode> docs = cosmosRepository.queryByType(tenantId, "project");

        ArrayNode response = objectMapper.createArrayNode();
        for (JsonNode doc : docs) {
            ObjectNode project = objectMapper.createObjectNode();
            project.put("projectId", doc.get("projectId").asText());
            project.put("tenantId", doc.get("tenantId").asText());
            project.put("name", doc.get("name").asText());
            if (doc.has("description") && !doc.get("description").isNull()) {
                project.put("description", doc.get("description").asText());
            }
            if (doc.has("createdAt")) {
                project.put("createdAt", doc.get("createdAt").asText());
            }
            response.add(project);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<JsonNode> getProject(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        try {
            JsonNode doc = cosmosRepository.findByField(tenantId, "project", "projectId", projectId);
            if (doc == null) {
                return ResponseEntity.notFound().build();
            }

            ObjectNode response = objectMapper.createObjectNode();
            response.put("projectId", doc.get("projectId").asText());
            response.put("tenantId", doc.get("tenantId").asText());
            response.put("name", doc.get("name").asText());
            if (doc.has("description") && !doc.get("description").isNull()) {
                response.put("description", doc.get("description").asText());
            }
            if (doc.has("createdAt")) {
                response.put("createdAt", doc.get("createdAt").asText());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}

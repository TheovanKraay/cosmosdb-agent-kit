package com.multitenant.controller;

import com.azure.cosmos.models.CosmosItemResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.multitenant.repository.CosmosRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final CosmosRepository cosmosRepository;
    private final ObjectMapper objectMapper;

    public TenantController(CosmosRepository cosmosRepository) {
        this.cosmosRepository = cosmosRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping
    public ResponseEntity<JsonNode> createTenant(@RequestBody Map<String, Object> body) {
        String tenantId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("id", tenantId);
        doc.put("tenantId", tenantId);
        doc.put("type", "tenant");
        doc.put("schemaVersion", 1);
        doc.put("name", (String) body.get("name"));
        doc.put("plan", (String) body.get("plan"));
        doc.put("createdAt", now);

        JsonNode created = cosmosRepository.createItem(doc);

        // Return response without internal fields
        ObjectNode response = objectMapper.createObjectNode();
        response.put("tenantId", tenantId);
        response.put("name", (String) body.get("name"));
        response.put("plan", (String) body.get("plan"));
        response.put("createdAt", now);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<JsonNode> getTenant(@PathVariable String tenantId) {
        try {
            JsonNode doc = cosmosRepository.readItem(tenantId, tenantId, "tenant");
            if (doc == null) {
                return ResponseEntity.notFound().build();
            }

            ObjectNode response = objectMapper.createObjectNode();
            response.put("tenantId", doc.get("tenantId").asText());
            response.put("name", doc.get("name").asText());
            response.put("plan", doc.get("plan").asText());
            if (doc.has("createdAt")) {
                response.put("createdAt", doc.get("createdAt").asText());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.notFound().build();
        }
    }
}

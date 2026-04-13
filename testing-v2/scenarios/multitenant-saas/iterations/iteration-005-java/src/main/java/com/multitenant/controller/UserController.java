package com.multitenant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.multitenant.repository.CosmosRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
public class UserController {

    private final CosmosRepository cosmosRepository;
    private final ObjectMapper objectMapper;

    public UserController(CosmosRepository cosmosRepository) {
        this.cosmosRepository = cosmosRepository;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping
    public ResponseEntity<JsonNode> createUser(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> body) {
        String userId = UUID.randomUUID().toString();

        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("id", userId);
        doc.put("tenantId", tenantId);
        doc.put("type", "user");
        doc.put("schemaVersion", 1);
        doc.put("userId", userId);
        doc.put("name", (String) body.get("name"));
        doc.put("email", (String) body.get("email"));
        doc.put("role", (String) body.get("role"));

        cosmosRepository.createItem(doc);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("userId", userId);
        response.put("tenantId", tenantId);
        response.put("name", (String) body.get("name"));
        response.put("email", (String) body.get("email"));
        response.put("role", (String) body.get("role"));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<JsonNode> listUsers(@PathVariable String tenantId) {
        List<JsonNode> docs = cosmosRepository.queryByType(tenantId, "user");

        ArrayNode response = objectMapper.createArrayNode();
        for (JsonNode doc : docs) {
            ObjectNode user = objectMapper.createObjectNode();
            user.put("userId", doc.get("userId").asText());
            user.put("tenantId", doc.get("tenantId").asText());
            user.put("name", doc.get("name").asText());
            user.put("email", doc.get("email").asText());
            user.put("role", doc.get("role").asText());
            response.add(user);
        }

        return ResponseEntity.ok(response);
    }
}

package com.multitenant.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Cosmos DB repository layer that enforces tenant isolation.
 * Uses parameterized queries (rule: query-parameterized) and
 * hierarchical partition keys (rule: partition-hierarchical).
 */
@Repository
public class CosmosRepository {

    private final CosmosContainer container;
    private final ObjectMapper objectMapper;

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "projectId", "userId", "taskId", "tenantId", "assigneeId",
            "status", "priority", "role", "plan", "name", "email", "title"
    );

    public CosmosRepository(CosmosContainer container) {
        this.container = container;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a document with hierarchical partition key [tenantId, type].
     */
    public JsonNode createItem(JsonNode item) {
        String tenantId = item.get("tenantId").asText();
        String type = item.get("type").asText();
        PartitionKey partitionKey = new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build();

        CosmosItemResponse<JsonNode> response = container.createItem(
                item, partitionKey, new CosmosItemRequestOptions());
        return response.getItem();
    }

    /**
     * Point read with hierarchical partition key — most efficient read pattern.
     * Rule: query-point-read
     */
    public JsonNode readItem(String id, String tenantId, String type) {
        PartitionKey partitionKey = new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build();
        CosmosItemResponse<JsonNode> response = container.readItem(
                id, partitionKey, JsonNode.class);
        return response.getItem();
    }

    /**
     * Query documents by type within a tenant — scoped to partition prefix.
     * Rule: query-minimize-cross-partition, query-parameterized
     */
    public List<JsonNode> queryByType(String tenantId, String type) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = @type",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@type", type)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build());
        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, options, JsonNode.class);
        List<JsonNode> items = new ArrayList<>();
        results.forEach(items::add);
        return items;
    }

    /**
     * Find a specific document by a field within a tenant and type.
     * Rule: query-parameterized
     */
    public JsonNode findByField(String tenantId, String type, String fieldName, String fieldValue) {
        if (!ALLOWED_FIELDS.contains(fieldName)) {
            throw new IllegalArgumentException("Invalid field name: " + fieldName);
        }
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = @type AND c." + fieldName + " = @fieldValue",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@type", type),
                        new SqlParameter("@fieldValue", fieldValue)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build());
        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, options, JsonNode.class);
        List<JsonNode> items = new ArrayList<>();
        results.forEach(items::add);
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * Query tasks by status within a tenant — uses tenant partition prefix.
     * Rule: query-parameterized
     */
    public List<JsonNode> queryTasksByStatus(String tenantId, String status) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.status = @status",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@status", status)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());
        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, options, JsonNode.class);
        List<JsonNode> items = new ArrayList<>();
        results.forEach(items::add);
        return items;
    }

    /**
     * Query tasks by assignee within a tenant.
     * Rule: query-parameterized
     */
    public List<JsonNode> queryTasksByAssignee(String tenantId, String assigneeId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.assigneeId = @assigneeId",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@assigneeId", assigneeId)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());
        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, options, JsonNode.class);
        List<JsonNode> items = new ArrayList<>();
        results.forEach(items::add);
        return items;
    }

    /**
     * Query tasks by project within a tenant.
     * Rule: query-parameterized
     */
    public List<JsonNode> queryTasksByProject(String tenantId, String projectId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.projectId = @projectId",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@projectId", projectId)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());
        CosmosPagedIterable<JsonNode> results = container.queryItems(
                querySpec, options, JsonNode.class);
        List<JsonNode> items = new ArrayList<>();
        results.forEach(items::add);
        return items;
    }

    /**
     * Count documents by type and field value within a tenant.
     * Rule: query-parameterized
     */
    public int countByTypeAndField(String tenantId, String type, String field, String value) {
        if (!ALLOWED_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Invalid field name: " + field);
        }
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.tenantId = @tenantId AND c.type = @type AND c." + field + " = @value",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@type", type),
                        new SqlParameter("@value", value)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build());
        CosmosPagedIterable<Integer> results = container.queryItems(
                querySpec, options, Integer.class);
        List<Integer> counts = new ArrayList<>();
        results.forEach(counts::add);
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    /**
     * Count documents by type within a tenant.
     * Rule: query-parameterized
     */
    public int countByType(String tenantId, String type) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.tenantId = @tenantId AND c.type = @type",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@type", type)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build());
        CosmosPagedIterable<Integer> results = container.queryItems(
                querySpec, options, Integer.class);
        List<Integer> counts = new ArrayList<>();
        results.forEach(counts::add);
        return counts.isEmpty() ? 0 : counts.get(0);
    }
}

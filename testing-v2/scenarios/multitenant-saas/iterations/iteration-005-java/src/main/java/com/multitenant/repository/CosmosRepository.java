package com.multitenant.repository;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKeyDefinitionVersion;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multitenant.config.CosmosConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Cosmos DB repository layer that enforces tenant isolation.
 * Uses lazy initialization with retry for the CosmosClient to handle
 * emulator startup delays and SSL certificate issues.
 * Uses parameterized queries (rule: query-parameterized) and
 * hierarchical partition keys (rule: partition-hierarchical).
 */
@Component
public class CosmosRepository {

    private static final Logger logger = LoggerFactory.getLogger(CosmosRepository.class);
    private static final int MAX_RETRIES = 10;
    private static final String CONTAINER_NAME = "entities";

    private final CosmosConfig cosmosConfig;
    private final ObjectMapper objectMapper;

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "projectId", "userId", "taskId", "tenantId", "assigneeId",
            "status", "priority", "role", "plan", "name", "email", "title"
    );

    public CosmosRepository(CosmosConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Lazy-initialize CosmosClient, database, and container with retry.
     * Synchronized double-checked locking ensures singleton semantics.
     */
    private CosmosContainer getContainer() {
        if (container == null) {
            synchronized (this) {
                if (container == null) {
                    Exception lastException = null;
                    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                            logger.info("Initializing Cosmos DB (attempt {}/{})", attempt, MAX_RETRIES);

                            // Build client with adaptive connection mode
                            String endpoint = cosmosConfig.getEndpoint();
                            CosmosClientBuilder builder = new CosmosClientBuilder()
                                    .endpoint(endpoint)
                                    .key(cosmosConfig.getKey())
                                    .consistencyLevel(ConsistencyLevel.SESSION)
                                    .contentResponseOnWriteEnabled(true);

                            boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
                            if (isEmulator) {
                                logger.info("Using Gateway mode for Cosmos DB Emulator");
                                builder.gatewayMode(new GatewayConnectionConfig());
                            } else {
                                logger.info("Using Direct mode for production Cosmos DB");
                                builder.directMode(DirectConnectionConfig.getDefaultConfig());
                            }

                            cosmosClient = builder.buildClient();

                            // Create database with autoscale throughput
                            cosmosClient.createDatabaseIfNotExists(
                                    cosmosConfig.getDatabaseName(),
                                    ThroughputProperties.createAutoscaledThroughput(4000));
                            CosmosDatabase database = cosmosClient.getDatabase(cosmosConfig.getDatabaseName());

                            // Create container with hierarchical partition key
                            PartitionKeyDefinition pkDef = new PartitionKeyDefinition();
                            pkDef.setPaths(Arrays.asList("/tenantId", "/type"));
                            pkDef.setKind(PartitionKind.MULTI_HASH);
                            pkDef.setVersion(PartitionKeyDefinitionVersion.V2);

                            CosmosContainerProperties containerProps =
                                    new CosmosContainerProperties(CONTAINER_NAME, pkDef);

                            // Custom indexing policy
                            IndexingPolicy indexingPolicy = new IndexingPolicy();
                            indexingPolicy.setAutomatic(true);

                            List<IncludedPath> includedPaths = new ArrayList<>();
                            includedPaths.add(new IncludedPath("/*"));
                            indexingPolicy.setIncludedPaths(includedPaths);

                            List<ExcludedPath> excludedPaths = new ArrayList<>();
                            excludedPaths.add(new ExcludedPath("/\"_etag\"/?"));
                            excludedPaths.add(new ExcludedPath("/description/?"));
                            excludedPaths.add(new ExcludedPath("/email/?"));
                            indexingPolicy.setExcludedPaths(excludedPaths);

                            // Composite indexes
                            List<List<CompositePath>> compositeIndexes = new ArrayList<>();

                            List<CompositePath> statusPriority = new ArrayList<>();
                            statusPriority.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
                            statusPriority.add(new CompositePath().setPath("/priority").setOrder(CompositePathSortOrder.ASCENDING));
                            compositeIndexes.add(statusPriority);

                            List<CompositePath> assigneeStatus = new ArrayList<>();
                            assigneeStatus.add(new CompositePath().setPath("/assigneeId").setOrder(CompositePathSortOrder.ASCENDING));
                            assigneeStatus.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
                            compositeIndexes.add(assigneeStatus);

                            List<CompositePath> typeCreated = new ArrayList<>();
                            typeCreated.add(new CompositePath().setPath("/type").setOrder(CompositePathSortOrder.ASCENDING));
                            typeCreated.add(new CompositePath().setPath("/createdAt").setOrder(CompositePathSortOrder.DESCENDING));
                            compositeIndexes.add(typeCreated);

                            indexingPolicy.setCompositeIndexes(compositeIndexes);
                            containerProps.setIndexingPolicy(indexingPolicy);

                            database.createContainerIfNotExists(containerProps);
                            container = database.getContainer(CONTAINER_NAME);

                            logger.info("Cosmos DB initialized successfully");
                            return container;
                        } catch (Exception e) {
                            lastException = e;
                            logger.warn("Cosmos DB init attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                            if (cosmosClient != null) {
                                try { cosmosClient.close(); } catch (Exception ignored) {}
                                cosmosClient = null;
                            }
                            if (attempt < MAX_RETRIES) {
                                try {
                                    long backoff = (long) Math.pow(2, attempt) * 1000;
                                    Thread.sleep(backoff);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("Interrupted during Cosmos DB init retry", ie);
                                }
                            }
                        }
                    }
                    throw new RuntimeException("Failed to initialize Cosmos DB after " + MAX_RETRIES + " attempts", lastException);
                }
            }
        }
        return container;
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

        CosmosItemResponse<JsonNode> response = getContainer().createItem(
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
        CosmosItemResponse<JsonNode> response = getContainer().readItem(
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
        CosmosPagedIterable<JsonNode> results = getContainer().queryItems(
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
        CosmosPagedIterable<JsonNode> results = getContainer().queryItems(
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
        CosmosPagedIterable<JsonNode> results = getContainer().queryItems(
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
        CosmosPagedIterable<JsonNode> results = getContainer().queryItems(
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
        CosmosPagedIterable<JsonNode> results = getContainer().queryItems(
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
        CosmosPagedIterable<Integer> results = getContainer().queryItems(
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
        CosmosPagedIterable<Integer> results = getContainer().queryItems(
                querySpec, options, Integer.class);
        List<Integer> counts = new ArrayList<>();
        results.forEach(counts::add);
        return counts.isEmpty() ? 0 : counts.get(0);
    }
}

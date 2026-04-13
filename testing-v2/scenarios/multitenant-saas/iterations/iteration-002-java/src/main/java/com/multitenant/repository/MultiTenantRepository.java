package com.multitenant.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.multitenant.model.BaseEntity;
import com.multitenant.model.Project;
import com.multitenant.model.Task;
import com.multitenant.model.Tenant;
import com.multitenant.model.TenantAnalytics;
import com.multitenant.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class MultiTenantRepository {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantRepository.class);

    private final CosmosContainer container;

    public MultiTenantRepository(CosmosContainer container) {
        this.container = container;
    }

    // Rule 2.3: Build hierarchical partition key (tenantId, type)
    private PartitionKey buildPartitionKey(String tenantId, String type) {
        return new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build();
    }

    private void logRU(String operation, double requestCharge) {
        logger.debug("{} consumed {} RUs", operation, String.format("%.2f", requestCharge));
    }

    // ==================== Tenant Operations ====================

    public Tenant createTenant(Tenant tenant) {
        String tenantId = UUID.randomUUID().toString();
        tenant.setId(tenantId);
        tenant.setTenantId(tenantId);
        tenant.setType("tenant");
        tenant.setCreatedAt(Instant.now().toString());

        PartitionKey pk = buildPartitionKey(tenantId, "tenant");
        // Rule 4.11: Use getItem() to unwrap response
        CosmosItemResponse<Tenant> response = container.createItem(tenant, pk, null);
        logRU("createTenant", response.getRequestCharge());
        return response.getItem();
    }

    // Rule 3.7: Use point read for known ID and partition key
    public Tenant getTenant(String tenantId) {
        try {
            PartitionKey pk = buildPartitionKey(tenantId, "tenant");
            CosmosItemResponse<Tenant> response = container.readItem(
                    tenantId, pk, Tenant.class);
            logRU("getTenant", response.getRequestCharge());
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    // ==================== User Operations ====================

    public User createUser(String tenantId, User user) {
        String userId = UUID.randomUUID().toString();
        user.setId(tenantId + "_user_" + userId);
        user.setUserId(userId);
        user.setTenantId(tenantId);
        user.setType("user");

        PartitionKey pk = buildPartitionKey(tenantId, "user");
        CosmosItemResponse<User> response = container.createItem(user, pk, null);
        logRU("createUser", response.getRequestCharge());
        return response.getItem();
    }

    public List<User> listUsers(String tenantId) {
        // Rule 3.6: Parameterized queries
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'user'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "user"));

        return container.queryItems(query, options, User.class)
                .stream().collect(Collectors.toList());
    }

    // ==================== Project Operations ====================

    public Project createProject(String tenantId, Project project) {
        String projectId = UUID.randomUUID().toString();
        project.setId(tenantId + "_project_" + projectId);
        project.setProjectId(projectId);
        project.setTenantId(tenantId);
        project.setType("project");
        project.setCreatedAt(Instant.now().toString());

        PartitionKey pk = buildPartitionKey(tenantId, "project");
        CosmosItemResponse<Project> response = container.createItem(project, pk, null);
        logRU("createProject", response.getRequestCharge());
        return response.getItem();
    }

    public List<Project> listProjects(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'project'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "project"));

        return container.queryItems(query, options, Project.class)
                .stream().collect(Collectors.toList());
    }

    public Project getProject(String tenantId, String projectId) {
        // Query by projectId field since document id is composite
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'project' AND c.projectId = @projectId",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@projectId", projectId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "project"));

        List<Project> results = container.queryItems(query, options, Project.class)
                .stream().collect(Collectors.toList());
        return results.isEmpty() ? null : results.get(0);
    }

    // ==================== Task Operations ====================

    public Task createTask(String tenantId, String projectId, Task task) {
        String taskId = UUID.randomUUID().toString();
        task.setId(tenantId + "_task_" + taskId);
        task.setTaskId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setType("task");
        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("todo");
        }
        task.setCreatedAt(Instant.now().toString());

        PartitionKey pk = buildPartitionKey(tenantId, "task");
        CosmosItemResponse<Task> response = container.createItem(task, pk, null);
        logRU("createTask", response.getRequestCharge());
        return response.getItem();
    }

    public List<Task> listTasksByProject(String tenantId, String projectId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.projectId = @projectId",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@projectId", projectId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "task"));

        return container.queryItems(query, options, Task.class)
                .stream().collect(Collectors.toList());
    }

    public List<Task> listTasksByUser(String tenantId, String userId) {
        // Cross-partition within tenant: tasks assigned to user across all projects
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.assigneeId = @userId",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@userId", userId)));

        // HPK prefix query: specify only tenantId + type level
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "task"));

        return container.queryItems(query, options, Task.class)
                .stream().collect(Collectors.toList());
    }

    public List<Task> listTasksByStatus(String tenantId, String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.status = @status",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@status", status)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "task"));

        return container.queryItems(query, options, Task.class)
                .stream().collect(Collectors.toList());
    }

    // ==================== Analytics ====================

    public TenantAnalytics getAnalytics(String tenantId) {
        TenantAnalytics analytics = new TenantAnalytics();
        analytics.setTenantId(tenantId);

        // Count users
        analytics.setTotalUsers(countByType(tenantId, "user"));

        // Count projects
        analytics.setTotalProjects(countByType(tenantId, "project"));

        // Count tasks and aggregate by status/priority
        List<Task> allTasks = getAllTasks(tenantId);
        analytics.setTotalTasks(allTasks.size());

        // Rule 3.4: Aggregate in application code to minimize cross-partition queries
        Map<String, Integer> tasksByStatus = new LinkedHashMap<>();
        tasksByStatus.put("todo", 0);
        tasksByStatus.put("in-progress", 0);
        tasksByStatus.put("done", 0);
        tasksByStatus.put("blocked", 0);

        Map<String, Integer> tasksByPriority = new LinkedHashMap<>();
        tasksByPriority.put("low", 0);
        tasksByPriority.put("medium", 0);
        tasksByPriority.put("high", 0);
        tasksByPriority.put("critical", 0);

        for (Task task : allTasks) {
            String status = task.getStatus();
            if (status != null && tasksByStatus.containsKey(status)) {
                tasksByStatus.merge(status, 1, Integer::sum);
            }
            String priority = task.getPriority();
            if (priority != null && tasksByPriority.containsKey(priority)) {
                tasksByPriority.merge(priority, 1, Integer::sum);
            }
        }

        analytics.setTasksByStatus(tasksByStatus);
        analytics.setTasksByPriority(tasksByPriority);

        return analytics;
    }

    private int countByType(String tenantId, String type) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.tenantId = @tenantId AND c.type = @type",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@type", type)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, type));

        List<Integer> results = container.queryItems(query, options, Integer.class)
                .stream().collect(Collectors.toList());
        return results.isEmpty() ? 0 : results.get(0);
    }

    private List<Task> getAllTasks(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(buildPartitionKey(tenantId, "task"));

        return container.queryItems(query, options, Task.class)
                .stream().collect(Collectors.toList());
    }
}

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
import com.multitenant.model.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository for multi-tenant data access.
 * Rule 3.6: Use parameterized queries.
 * Rule 3.1: Minimize cross-partition queries — scope all queries to tenantId partition.
 * Rule 2.3: Hierarchical partition key (/tenantId + /type).
 */
@Repository
public class MultitenantRepository {

    private final CosmosContainer container;

    public MultitenantRepository(CosmosContainer tenantsContainer) {
        this.container = tenantsContainer;
    }

    // ======================== TENANT OPERATIONS ========================

    public Tenant createTenant(String name, String plan) {
        Tenant tenant = new Tenant();
        String tenantId = UUID.randomUUID().toString();
        tenant.setId(tenantId);
        tenant.setTenantId(tenantId);
        tenant.setName(name);
        tenant.setPlan(plan);
        tenant.setCreatedAt(Instant.now().toString());

        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("tenant")
                .build();

        CosmosItemResponse<Tenant> response = container.createItem(tenant, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Optional<Tenant> getTenant(String tenantId) {
        try {
            PartitionKey pk = new PartitionKeyBuilder()
                    .add(tenantId)
                    .add("tenant")
                    .build();

            CosmosItemResponse<Tenant> response = container.readItem(tenantId, pk, Tenant.class);
            return Optional.ofNullable(response.getItem());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NotFound")) {
                return Optional.empty();
            }
            if (e instanceof com.azure.cosmos.CosmosException cosmosEx && cosmosEx.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // ======================== USER OPERATIONS ========================

    public User createUser(String tenantId, String name, String email, String role) {
        User user = new User();
        String userId = UUID.randomUUID().toString();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUserId(userId);
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);

        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("user")
                .build();

        CosmosItemResponse<User> response = container.createItem(user, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public List<User> listUsers(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'user'",
                List.of(new SqlParameter("@tenantId", tenantId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("user")
                .build());

        CosmosPagedIterable<User> results = container.queryItems(query, options, User.class);
        return results.stream().collect(Collectors.toList());
    }

    // ======================== PROJECT OPERATIONS ========================

    public Project createProject(String tenantId, String name, String description) {
        Project project = new Project();
        String projectId = UUID.randomUUID().toString();
        project.setId(projectId);
        project.setTenantId(tenantId);
        project.setProjectId(projectId);
        project.setName(name);
        project.setDescription(description != null ? description : "");
        project.setCreatedAt(Instant.now().toString());

        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("project")
                .build();

        CosmosItemResponse<Project> response = container.createItem(project, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public List<Project> listProjects(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'project'",
                List.of(new SqlParameter("@tenantId", tenantId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("project")
                .build());

        CosmosPagedIterable<Project> results = container.queryItems(query, options, Project.class);
        return results.stream().collect(Collectors.toList());
    }

    public Optional<Project> getProject(String tenantId, String projectId) {
        try {
            PartitionKey pk = new PartitionKeyBuilder()
                    .add(tenantId)
                    .add("project")
                    .build();

            CosmosItemResponse<Project> response = container.readItem(projectId, pk, Project.class);
            return Optional.ofNullable(response.getItem());
        } catch (Exception e) {
            if (e instanceof com.azure.cosmos.CosmosException cosmosEx && cosmosEx.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // ======================== TASK OPERATIONS ========================

    public Task createTask(String tenantId, String projectId, String title, String assigneeId,
                           String priority, String status) {
        Task task = new Task();
        String taskId = UUID.randomUUID().toString();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setTaskId(taskId);
        task.setProjectId(projectId);
        task.setTitle(title);
        task.setAssigneeId(assigneeId);
        task.setPriority(priority);
        task.setStatus(status != null ? status : "todo");
        task.setCreatedAt(Instant.now().toString());

        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build();

        CosmosItemResponse<Task> response = container.createItem(task, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public List<Task> listProjectTasks(String tenantId, String projectId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.projectId = @projectId",
                List.of(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@projectId", projectId)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());

        CosmosPagedIterable<Task> results = container.queryItems(query, options, Task.class);
        return results.stream().collect(Collectors.toList());
    }

    public List<Task> listUserTasks(String tenantId, String userId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.assigneeId = @userId",
                List.of(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@userId", userId)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());

        CosmosPagedIterable<Task> results = container.queryItems(query, options, Task.class);
        return results.stream().collect(Collectors.toList());
    }

    public List<Task> listTasksByStatus(String tenantId, String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.status = @status",
                List.of(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@status", status)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());

        CosmosPagedIterable<Task> results = container.queryItems(query, options, Task.class);
        return results.stream().collect(Collectors.toList());
    }

    // ======================== ANALYTICS ========================

    public AnalyticsResponse getAnalytics(String tenantId) {
        AnalyticsResponse analytics = new AnalyticsResponse();
        analytics.setTenantId(tenantId);

        // Count users
        analytics.setTotalUsers(countByType(tenantId, "user"));

        // Count projects
        analytics.setTotalProjects(countByType(tenantId, "project"));

        // Get all tasks for this tenant to compute breakdowns
        SqlQuerySpec taskQuery = new SqlQuerySpec(
                "SELECT c.status, c.priority FROM c WHERE c.tenantId = @tenantId AND c.type = 'task'",
                List.of(new SqlParameter("@tenantId", tenantId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());

        CosmosPagedIterable<Task> tasks = container.queryItems(taskQuery, options, Task.class);
        List<Task> taskList = tasks.stream().collect(Collectors.toList());

        analytics.setTotalTasks(taskList.size());

        // Tasks by status
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("todo", 0);
        byStatus.put("in-progress", 0);
        byStatus.put("done", 0);
        byStatus.put("blocked", 0);
        for (Task t : taskList) {
            byStatus.merge(t.getStatus(), 1, Integer::sum);
        }
        analytics.setTasksByStatus(byStatus);

        // Tasks by priority
        Map<String, Integer> byPriority = new LinkedHashMap<>();
        byPriority.put("low", 0);
        byPriority.put("medium", 0);
        byPriority.put("high", 0);
        byPriority.put("critical", 0);
        for (Task t : taskList) {
            byPriority.merge(t.getPriority(), 1, Integer::sum);
        }
        analytics.setTasksByPriority(byPriority);

        return analytics;
    }

    private int countByType(String tenantId, String type) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT VALUE COUNT(1) FROM c WHERE c.tenantId = @tenantId AND c.type = @type",
                List.of(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@type", type)
                )
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .build());

        CosmosPagedIterable<Integer> results = container.queryItems(query, options, Integer.class);
        return results.stream().findFirst().orElse(0);
    }
}

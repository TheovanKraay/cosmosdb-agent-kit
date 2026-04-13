package com.multitenant.saas.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.multitenant.saas.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-tenant repository with tenant isolation enforced on all queries.
 *
 * Best practices applied:
 * - Rule 3.1: All queries include partition key (tenantId) in WHERE
 * - Rule 3.6: Parameterized queries with SqlParameter (no string concat)
 * - Rule 2.3: Hierarchical partition key routing via PartitionKeyBuilder
 * - Rule 3.9: Project only needed fields where possible
 */
@Repository
public class MultiTenantRepository {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantRepository.class);

    private final CosmosContainer container;

    public MultiTenantRepository(CosmosContainer container) {
        this.container = container;
    }

    // ─── Tenant Operations ───

    public Tenant createTenant(Tenant tenant) {
        String tenantId = UUID.randomUUID().toString();
        tenant.setTenantId(tenantId);
        tenant.setId(tenantId);
        tenant.setType("tenant");
        tenant.setCreatedAt(Instant.now().toString());
        tenant.setSchemaVersion(1);

        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("tenant")
                .build();

        CosmosItemResponse<Tenant> response = container.createItem(tenant, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Tenant getTenant(String tenantId) {
        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("tenant")
                .build();

        try {
            CosmosItemResponse<Tenant> response = container.readItem(tenantId, pk, Tenant.class);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    // ─── User Operations ───

    public User createUser(String tenantId, User user) {
        String userId = UUID.randomUUID().toString();
        user.setUserId(userId);
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setType("user");
        user.setSchemaVersion(1);

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
                Collections.singletonList(new SqlParameter("@tenantId", tenantId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("user")
                .build());

        CosmosPagedIterable<User> results = container.queryItems(query, options, User.class);
        return results.stream().collect(Collectors.toList());
    }

    // ─── Project Operations ───

    public Project createProject(String tenantId, Project project) {
        String projectId = UUID.randomUUID().toString();
        project.setProjectId(projectId);
        project.setId(projectId);
        project.setTenantId(tenantId);
        project.setType("project");
        project.setCreatedAt(Instant.now().toString());
        project.setSchemaVersion(1);

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
                Collections.singletonList(new SqlParameter("@tenantId", tenantId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("project")
                .build());

        CosmosPagedIterable<Project> results = container.queryItems(query, options, Project.class);
        return results.stream().collect(Collectors.toList());
    }

    public Project getProject(String tenantId, String projectId) {
        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenantId)
                .add("project")
                .build();

        try {
            CosmosItemResponse<Project> response = container.readItem(projectId, pk, Project.class);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    // ─── Task Operations ───

    public Task createTask(String tenantId, String projectId, Task task) {
        String taskId = UUID.randomUUID().toString();
        task.setTaskId(taskId);
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setType("task");
        task.setCreatedAt(Instant.now().toString());
        task.setSchemaVersion(1);

        if (task.getStatus() == null || task.getStatus().isEmpty()) {
            task.setStatus("todo");
        }

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

        CosmosPagedIterable<Task> results = container.queryItems(query, options, Task.class);
        return results.stream().collect(Collectors.toList());
    }

    public List<Task> getUserTasks(String tenantId, String userId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.assigneeId = @userId",
                Arrays.asList(
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

    public List<Task> getTasksByStatus(String tenantId, String status) {
        SqlQuerySpec query = new SqlQuerySpec(
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

        CosmosPagedIterable<Task> results = container.queryItems(query, options, Task.class);
        return results.stream().collect(Collectors.toList());
    }

    // ─── Analytics ───

    public TenantAnalytics getAnalytics(String tenantId) {
        TenantAnalytics analytics = new TenantAnalytics();
        analytics.setTenantId(tenantId);

        // Count users
        int userCount = countByType(tenantId, "user");
        analytics.setTotalUsers(userCount);

        // Count projects
        int projectCount = countByType(tenantId, "project");
        analytics.setTotalProjects(projectCount);

        // Get all tasks for analytics
        List<Task> allTasks = getAllTasks(tenantId);
        analytics.setTotalTasks(allTasks.size());

        // Tasks by status
        Map<String, Integer> tasksByStatus = new LinkedHashMap<>();
        tasksByStatus.put("todo", 0);
        tasksByStatus.put("in-progress", 0);
        tasksByStatus.put("done", 0);
        tasksByStatus.put("blocked", 0);
        for (Task task : allTasks) {
            String status = task.getStatus();
            if (status != null) {
                tasksByStatus.merge(status, 1, Integer::sum);
            }
        }
        analytics.setTasksByStatus(tasksByStatus);

        // Tasks by priority
        Map<String, Integer> tasksByPriority = new LinkedHashMap<>();
        tasksByPriority.put("low", 0);
        tasksByPriority.put("medium", 0);
        tasksByPriority.put("high", 0);
        tasksByPriority.put("critical", 0);
        for (Task task : allTasks) {
            String priority = task.getPriority();
            if (priority != null) {
                tasksByPriority.merge(priority, 1, Integer::sum);
            }
        }
        analytics.setTasksByPriority(tasksByPriority);

        return analytics;
    }

    private int countByType(String tenantId, String type) {
        SqlQuerySpec query = new SqlQuerySpec(
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

        CosmosPagedIterable<Integer> results = container.queryItems(query, options, Integer.class);
        return results.stream().findFirst().orElse(0);
    }

    private List<Task> getAllTasks(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task'",
                Collections.singletonList(new SqlParameter("@tenantId", tenantId))
        );

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());

        CosmosPagedIterable<Task> results = container.queryItems(query, options, Task.class);
        return results.stream().collect(Collectors.toList());
    }
}

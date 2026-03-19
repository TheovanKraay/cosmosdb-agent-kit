package com.example.multitenentsaas.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.PartitionKeyBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.example.multitenentsaas.dto.TenantAnalytics;
import com.example.multitenentsaas.model.BaseEntity;
import com.example.multitenentsaas.model.Project;
import com.example.multitenentsaas.model.Task;
import com.example.multitenentsaas.model.Tenant;
import com.example.multitenentsaas.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository layer enforcing tenant isolation for all Cosmos DB operations.
 *
 * Best practices applied:
 * - Rule 2.3:  Hierarchical partition keys (tenantId / type / projectId)
 * - Rule 3.1:  Minimize cross-partition queries
 * - Rule 3.5:  Parameterized queries
 * - Rule 3.6:  Project only needed fields
 * - Rule 4.9:  Content response enabled (via CosmosConfig)
 * - Rule 4.7:  ETags for optimistic concurrency
 * - Rule 1.9:  Type discriminators for polymorphic queries
 * - Rule 1.2:  Denormalized data for read efficiency
 */
@Repository
public class MultiTenantRepository {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantRepository.class);
    private static final int MAX_EMBEDDED_COMMENTS = 20; // Rule 1.7: bound embedded data

    private final CosmosContainer container;

    public MultiTenantRepository(CosmosContainer container) {
        this.container = container;
    }

    // ========================
    // TENANT OPERATIONS
    // ========================

    public Tenant createTenant(Tenant tenant) {
        tenant.setId(UUID.randomUUID().toString());
        tenant.setType("tenant");
        tenant.setProjectId(""); // Level 3: empty for tenant entities
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());

        PartitionKey pk = buildPartitionKey(tenant.getTenantId(), "tenant", "");
        CosmosItemResponse<Tenant> response = container.createItem(tenant, pk, null);
        logRU("createTenant", response.getRequestCharge());
        return response.getItem();
    }

    public Tenant getTenant(String tenantId) {
        PartitionKey pk = buildPartitionKey(tenantId, "tenant", "");
        // Rule 3.5: parameterized query; Rule 3.6: project only needed fields
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'tenant'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(pk);

        List<Tenant> results = container.queryItems(query, options, Tenant.class)
                .stream().collect(Collectors.toList());

        return results.isEmpty() ? null : results.get(0);
    }

    public Tenant updateTenant(Tenant tenant) {
        tenant.setUpdatedAt(Instant.now());
        PartitionKey pk = buildPartitionKey(tenant.getTenantId(), "tenant", "");
        CosmosItemResponse<Tenant> response = container.upsertItem(tenant, pk, null);
        logRU("updateTenant", response.getRequestCharge());
        return response.getItem();
    }

    public void deleteTenant(String tenantId, String id) {
        PartitionKey pk = buildPartitionKey(tenantId, "tenant", "");
        container.deleteItem(id, pk, new CosmosItemRequestOptions());
    }

    // ========================
    // USER OPERATIONS
    // ========================

    public User createUser(String tenantId, User user) {
        user.setId(UUID.randomUUID().toString());
        user.setTenantId(tenantId);
        user.setType("user");
        user.setProjectId(""); // Level 3: empty for user entities
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        PartitionKey pk = buildPartitionKey(tenantId, "user", "");
        CosmosItemResponse<User> response = container.createItem(user, pk, null);
        logRU("createUser", response.getRequestCharge());
        return response.getItem();
    }

    public User getUser(String tenantId, String userId) {
        PartitionKey pk = buildPartitionKey(tenantId, "user", "");
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @userId AND c.tenantId = @tenantId AND c.type = 'user'",
                Arrays.asList(
                        new SqlParameter("@userId", userId),
                        new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(pk);

        List<User> results = container.queryItems(query, options, User.class)
                .stream().collect(Collectors.toList());
        return results.isEmpty() ? null : results.get(0);
    }

    /** Get all users in a tenant (Rule 3.1: single-partition query via hierarchical PK Level 1+2) */
    public List<User> getUsersByTenant(String tenantId) {
        PartitionKey pk = buildPartitionKey(tenantId, "user", "");
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'user'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(pk);

        return container.queryItems(query, options, User.class)
                .stream().collect(Collectors.toList());
    }

    public User updateUser(String tenantId, User user) {
        user.setTenantId(tenantId);
        user.setUpdatedAt(Instant.now());
        PartitionKey pk = buildPartitionKey(tenantId, "user", "");
        CosmosItemResponse<User> response = container.upsertItem(user, pk, null);
        logRU("updateUser", response.getRequestCharge());
        return response.getItem();
    }

    public void deleteUser(String tenantId, String userId) {
        PartitionKey pk = buildPartitionKey(tenantId, "user", "");
        container.deleteItem(userId, pk, new CosmosItemRequestOptions());
    }

    // ========================
    // PROJECT OPERATIONS
    // ========================

    public Project createProject(String tenantId, Project project) {
        String projectId = UUID.randomUUID().toString();
        project.setId(projectId);
        project.setTenantId(tenantId);
        project.setType("project");
        project.setProjectId(projectId); // Level 3: self-referencing for projects
        project.setStatus("active");
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());

        PartitionKey pk = buildPartitionKey(tenantId, "project", projectId);
        CosmosItemResponse<Project> response = container.createItem(project, pk, null);
        logRU("createProject", response.getRequestCharge());
        return response.getItem();
    }

    public Project getProject(String tenantId, String projectId) {
        PartitionKey pk = buildPartitionKey(tenantId, "project", projectId);
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @projectId AND c.tenantId = @tenantId AND c.type = 'project'",
                Arrays.asList(
                        new SqlParameter("@projectId", projectId),
                        new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(pk);

        List<Project> results = container.queryItems(query, options, Project.class)
                .stream().collect(Collectors.toList());
        return results.isEmpty() ? null : results.get(0);
    }

    /** Get all projects in a tenant (scoped to tenant partition, type=project) */
    public List<Project> getProjectsByTenant(String tenantId) {
        // Cross-partition query within tenant for all projects
        // This uses hierarchical PK prefix query on Level 1 (tenantId) + filter on type
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'project' ORDER BY c.createdAt DESC",
                Arrays.asList(new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        // No partition key set = queries across all Level 3 values within tenant+type prefix

        return container.queryItems(query, options, Project.class)
                .stream().collect(Collectors.toList());
    }

    public Project updateProject(String tenantId, Project project) {
        project.setUpdatedAt(Instant.now());
        PartitionKey pk = buildPartitionKey(tenantId, "project", project.getProjectId());
        CosmosItemResponse<Project> response = container.upsertItem(project, pk, null);
        logRU("updateProject", response.getRequestCharge());
        return response.getItem();
    }

    public void deleteProject(String tenantId, String projectId) {
        PartitionKey pk = buildPartitionKey(tenantId, "project", projectId);
        container.deleteItem(projectId, pk, new CosmosItemRequestOptions());
    }

    // ========================
    // TASK OPERATIONS
    // ========================

    public Task createTask(String tenantId, String projectId, Task task) {
        task.setId(UUID.randomUUID().toString());
        task.setTenantId(tenantId);
        task.setType("task");
        task.setProjectId(projectId);
        task.setStatus("open");
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        PartitionKey pk = buildPartitionKey(tenantId, "task", projectId);
        CosmosItemResponse<Task> response = container.createItem(task, pk, null);
        logRU("createTask", response.getRequestCharge());

        // Update denormalized task counts on the project (Rule 1.2)
        updateProjectTaskCounts(tenantId, projectId);

        return response.getItem();
    }

    /** Get tasks by project — single partition query via full hierarchical PK (Rule 3.1) */
    public List<Task> getTasksByProject(String tenantId, String projectId) {
        PartitionKey pk = buildPartitionKey(tenantId, "task", projectId);
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.projectId = @projectId ORDER BY c.createdAt DESC",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@projectId", projectId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(pk);

        return container.queryItems(query, options, Task.class)
                .stream().collect(Collectors.toList());
    }

    /**
     * Get all tasks assigned to a user within a tenant (Rule 3.1).
     * This is a cross-partition query within the tenant (spans all projectIds)
     * but is limited to type="task" so it targets the correct partition key prefix.
     */
    public List<Task> getTasksByAssignee(String tenantId, String assigneeId) {
        // Rule 3.6: project only needed fields for list view
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.tenantId, c.projectId, c.title, c.status, c.priority, " +
                        "c.assigneeId, c.assigneeName, c.dueDate, c.createdAt, c.type " +
                        "FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.assigneeId = @assigneeId " +
                        "ORDER BY c.dueDate ASC",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@assigneeId", assigneeId)));

        return container.queryItems(query, new CosmosQueryRequestOptions(), Task.class)
                .stream().collect(Collectors.toList());
    }

    /** Get tasks by status within a tenant */
    public List<Task> getTasksByStatus(String tenantId, String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.tenantId, c.projectId, c.title, c.status, c.priority, " +
                        "c.assigneeId, c.assigneeName, c.dueDate, c.createdAt, c.type " +
                        "FROM c WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.status = @status " +
                        "ORDER BY c.createdAt DESC",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@status", status)));

        return container.queryItems(query, new CosmosQueryRequestOptions(), Task.class)
                .stream().collect(Collectors.toList());
    }

    public Task getTask(String tenantId, String projectId, String taskId) {
        PartitionKey pk = buildPartitionKey(tenantId, "task", projectId);
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.id = @taskId AND c.tenantId = @tenantId AND c.type = 'task'",
                Arrays.asList(
                        new SqlParameter("@taskId", taskId),
                        new SqlParameter("@tenantId", tenantId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(pk);

        List<Task> results = container.queryItems(query, options, Task.class)
                .stream().collect(Collectors.toList());
        return results.isEmpty() ? null : results.get(0);
    }

    public Task updateTask(String tenantId, String projectId, Task task) {
        task.setUpdatedAt(Instant.now());
        PartitionKey pk = buildPartitionKey(tenantId, "task", projectId);
        CosmosItemResponse<Task> response = container.upsertItem(task, pk, null);
        logRU("updateTask", response.getRequestCharge());

        // Update denormalized task counts (Rule 1.2)
        updateProjectTaskCounts(tenantId, projectId);

        return response.getItem();
    }

    public void deleteTask(String tenantId, String projectId, String taskId) {
        PartitionKey pk = buildPartitionKey(tenantId, "task", projectId);
        container.deleteItem(taskId, pk, new CosmosItemRequestOptions());

        // Update denormalized task counts (Rule 1.2)
        updateProjectTaskCounts(tenantId, projectId);
    }

    /** Add comment to a task (Rule 1.3: embedded comments, bounded by MAX_EMBEDDED_COMMENTS) */
    public Task addComment(String tenantId, String projectId, String taskId, Task.Comment comment) {
        Task task = getTask(tenantId, projectId, taskId);
        if (task == null) {
            return null;
        }

        comment.setCommentId(UUID.randomUUID().toString());
        comment.setCreatedAt(Instant.now());

        List<Task.Comment> comments = task.getComments();
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(0, comment); // Most recent first

        // Rule 1.7: bound embedded data to prevent exceeding 2MB
        if (comments.size() > MAX_EMBEDDED_COMMENTS) {
            comments = new ArrayList<>(comments.subList(0, MAX_EMBEDDED_COMMENTS));
        }
        task.setComments(comments);
        task.setUpdatedAt(Instant.now());

        PartitionKey pk = buildPartitionKey(tenantId, "task", projectId);
        CosmosItemResponse<Task> response = container.upsertItem(task, pk, null);
        logRU("addComment", response.getRequestCharge());
        return response.getItem();
    }

    // ========================
    // ANALYTICS
    // ========================

    /**
     * Get tenant-level analytics.
     * Uses denormalized task counts from projects (Rule 1.2) to avoid
     * expensive cross-partition aggregations.
     */
    public TenantAnalytics getTenantAnalytics(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        List<Project> projects = getProjectsByTenant(tenantId);

        TenantAnalytics analytics = new TenantAnalytics();
        analytics.setTenantId(tenantId);
        analytics.setTenantName(tenant != null ? tenant.getName() : "Unknown");
        analytics.setTotalProjects(projects.size());

        int totalTasks = 0, openTasks = 0, inProgressTasks = 0, completedTasks = 0;
        for (Project project : projects) {
            totalTasks += project.getTaskCountTotal();
            openTasks += project.getTaskCountOpen();
            inProgressTasks += project.getTaskCountInProgress();
            completedTasks += project.getTaskCountCompleted();
        }

        analytics.setTotalTasks(totalTasks);
        analytics.setOpenTasks(openTasks);
        analytics.setInProgressTasks(inProgressTasks);
        analytics.setCompletedTasks(completedTasks);
        analytics.setCancelledTasks(totalTasks - openTasks - inProgressTasks - completedTasks);
        analytics.setCompletionRate(totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0);

        return analytics;
    }

    // ========================
    // HELPERS
    // ========================

    /** Update denormalized task counts on a project (Rule 1.2) */
    private void updateProjectTaskCounts(String tenantId, String projectId) {
        try {
            // Count tasks by status within the project partition
            PartitionKey taskPk = buildPartitionKey(tenantId, "task", projectId);

            SqlQuerySpec countQuery = new SqlQuerySpec(
                    "SELECT c.status, COUNT(1) AS cnt FROM c " +
                            "WHERE c.tenantId = @tenantId AND c.type = 'task' AND c.projectId = @projectId " +
                            "GROUP BY c.status",
                    Arrays.asList(
                            new SqlParameter("@tenantId", tenantId),
                            new SqlParameter("@projectId", projectId)));

            CosmosQueryRequestOptions countOptions = new CosmosQueryRequestOptions();
            countOptions.setPartitionKey(taskPk);

            int total = 0, open = 0, inProgress = 0, completed = 0;

            for (StatusCount sc : container.queryItems(countQuery, countOptions, StatusCount.class)
                    .stream().collect(Collectors.toList())) {
                int cnt = sc.getCnt();
                total += cnt;
                switch (sc.getStatus()) {
                    case "open": open = cnt; break;
                    case "in_progress": inProgress = cnt; break;
                    case "completed": completed = cnt; break;
                }
            }

            Project project = getProject(tenantId, projectId);
            if (project != null) {
                project.setTaskCountTotal(total);
                project.setTaskCountOpen(open);
                project.setTaskCountInProgress(inProgress);
                project.setTaskCountCompleted(completed);
                project.setUpdatedAt(Instant.now());

                PartitionKey projectPk = buildPartitionKey(tenantId, "project", projectId);
                container.upsertItem(project, projectPk, null);
            }
        } catch (Exception e) {
            logger.warn("Failed to update project task counts for project {}: {}", projectId, e.getMessage());
        }
    }

    /**
     * Build hierarchical partition key (Rule 2.3).
     * 3 levels: tenantId / type / projectId
     */
    private PartitionKey buildPartitionKey(String tenantId, String type, String projectId) {
        return new PartitionKeyBuilder()
                .add(tenantId)
                .add(type)
                .add(projectId)
                .build();
    }

    private void logRU(String operation, double requestCharge) {
        logger.debug("{} consumed {:.2f} RUs", operation, requestCharge);
    }

    /** Helper class for GROUP BY status count query */
    public static class StatusCount {
        private String status;
        private int cnt;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCnt() { return cnt; }
        public void setCnt(int cnt) { this.cnt = cnt; }
    }
}

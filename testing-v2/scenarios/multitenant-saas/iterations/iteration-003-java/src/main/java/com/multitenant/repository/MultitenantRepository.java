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
import com.multitenant.model.BaseDocument;
import com.multitenant.model.Project;
import com.multitenant.model.Task;
import com.multitenant.model.Tenant;
import com.multitenant.model.User;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Repository
public class MultitenantRepository {

    private final CosmosContainer container;

    public MultitenantRepository(CosmosContainer multitenantContainer) {
        this.container = multitenantContainer;
    }

    // ---- Tenant Operations ----

    public Tenant createTenant(Tenant tenant) {
        PartitionKey pk = new PartitionKeyBuilder()
                .add(tenant.getTenantId())
                .add(tenant.getType())
                .build();
        CosmosItemResponse<Tenant> response = container.createItem(tenant, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public Tenant getTenant(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'tenant'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("tenant")
                .build());
        CosmosPagedIterable<Tenant> results = container.queryItems(query, options, Tenant.class);
        for (Tenant t : results) {
            return t;
        }
        return null;
    }

    // ---- User Operations ----

    public User createUser(User user) {
        PartitionKey pk = new PartitionKeyBuilder()
                .add(user.getTenantId())
                .add(user.getType())
                .build();
        CosmosItemResponse<User> response = container.createItem(user, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public List<User> listUsers(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'user'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("user")
                .build());
        List<User> users = new ArrayList<>();
        container.queryItems(query, options, User.class).forEach(users::add);
        return users;
    }

    // ---- Project Operations ----

    public Project createProject(Project project) {
        PartitionKey pk = new PartitionKeyBuilder()
                .add(project.getTenantId())
                .add(project.getType())
                .build();
        CosmosItemResponse<Project> response = container.createItem(project, pk, new CosmosItemRequestOptions());
        return response.getItem();
    }

    public List<Project> listProjects(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'project'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("project")
                .build());
        List<Project> projects = new ArrayList<>();
        container.queryItems(query, options, Project.class).forEach(projects::add);
        return projects;
    }

    public Project getProject(String tenantId, String projectId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'project' AND c.projectId = @projectId",
                Arrays.asList(
                        new SqlParameter("@tenantId", tenantId),
                        new SqlParameter("@projectId", projectId)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("project")
                .build());
        for (Project p : container.queryItems(query, options, Project.class)) {
            return p;
        }
        return null;
    }

    // ---- Task Operations ----

    public Task createTask(Task task) {
        PartitionKey pk = new PartitionKeyBuilder()
                .add(task.getTenantId())
                .add(task.getType())
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
        List<Task> tasks = new ArrayList<>();
        container.queryItems(query, options, Task.class).forEach(tasks::add);
        return tasks;
    }

    public List<Task> listUserTasks(String tenantId, String userId) {
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
        List<Task> tasks = new ArrayList<>();
        container.queryItems(query, options, Task.class).forEach(tasks::add);
        return tasks;
    }

    public List<Task> listTasksByStatus(String tenantId, String status) {
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
        List<Task> tasks = new ArrayList<>();
        container.queryItems(query, options, Task.class).forEach(tasks::add);
        return tasks;
    }

    public List<Task> listAllTasks(String tenantId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.tenantId = @tenantId AND c.type = 'task'",
                Arrays.asList(new SqlParameter("@tenantId", tenantId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKeyBuilder()
                .add(tenantId)
                .add("task")
                .build());
        List<Task> tasks = new ArrayList<>();
        container.queryItems(query, options, Task.class).forEach(tasks::add);
        return tasks;
    }

    // ---- Analytics helpers ----

    public int countByType(String tenantId, String type) {
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
        for (Integer count : container.queryItems(query, options, Integer.class)) {
            return count;
        }
        return 0;
    }
}

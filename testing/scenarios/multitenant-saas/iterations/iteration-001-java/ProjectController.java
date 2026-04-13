package com.example.multitenentsaas.controller;

import com.example.multitenentsaas.model.Project;
import com.example.multitenentsaas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for project management within a tenant.
 * Tenant isolation enforced via tenantId path parameter.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects")
public class ProjectController {

    private final MultiTenantRepository repository;

    public ProjectController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@PathVariable String tenantId, @RequestBody Project project) {
        Project created = repository.createProject(tenantId, project);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Project>> getProjects(@PathVariable String tenantId) {
        List<Project> projects = repository.getProjectsByTenant(tenantId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProject(@PathVariable String tenantId, @PathVariable String projectId) {
        Project project = repository.getProject(tenantId, projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(project);
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<Project> updateProject(@PathVariable String tenantId, @PathVariable String projectId,
                                                  @RequestBody Project project) {
        project.setId(projectId);
        project.setProjectId(projectId);
        project.setTenantId(tenantId);
        Project updated = repository.updateProject(tenantId, project);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable String tenantId, @PathVariable String projectId) {
        repository.deleteProject(tenantId, projectId);
        return ResponseEntity.noContent().build();
    }
}

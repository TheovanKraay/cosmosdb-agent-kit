package com.multitenant.saas.controller;

import com.multitenant.saas.model.Project;
import com.multitenant.saas.repository.MultiTenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects")
public class ProjectController {

    private final MultiTenantRepository repository;

    public ProjectController(MultiTenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(
            @PathVariable String tenantId,
            @RequestBody Project project) {
        Project created = repository.createProject(tenantId, project);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Project>> listProjects(@PathVariable String tenantId) {
        List<Project> projects = repository.listProjects(tenantId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProject(
            @PathVariable String tenantId,
            @PathVariable String projectId) {
        Project project = repository.getProject(tenantId, projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(project);
    }
}

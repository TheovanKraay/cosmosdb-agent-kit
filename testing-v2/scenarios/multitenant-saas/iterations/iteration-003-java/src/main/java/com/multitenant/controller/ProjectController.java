package com.multitenant.controller;

import com.multitenant.model.Project;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects")
public class ProjectController {

    private final MultitenantRepository repository;

    public ProjectController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(
            @PathVariable String tenantId,
            @RequestBody Map<String, String> body) {
        Project project = new Project();
        String projectId = UUID.randomUUID().toString();
        project.setId(projectId);
        project.setProjectId(projectId);
        project.setTenantId(tenantId);
        project.setName(body.get("name"));
        project.setDescription(body.getOrDefault("description", ""));
        project.setCreatedAt(Instant.now().toString());

        Project created = repository.createProject(project);
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

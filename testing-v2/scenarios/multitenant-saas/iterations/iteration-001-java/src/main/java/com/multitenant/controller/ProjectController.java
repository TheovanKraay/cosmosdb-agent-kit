package com.multitenant.controller;

import com.multitenant.model.Project;
import com.multitenant.repository.MultitenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tenants/{tenantId}/projects")
public class ProjectController {

    private final MultitenantRepository repository;

    public ProjectController(MultitenantRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@PathVariable String tenantId,
                                                  @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.getOrDefault("description", null);
        Project project = repository.createProject(tenantId, name, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public ResponseEntity<List<Project>> listProjects(@PathVariable String tenantId) {
        List<Project> projects = repository.listProjects(tenantId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Project> getProject(@PathVariable String tenantId,
                                               @PathVariable String projectId) {
        Optional<Project> project = repository.getProject(tenantId, projectId);
        return project.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

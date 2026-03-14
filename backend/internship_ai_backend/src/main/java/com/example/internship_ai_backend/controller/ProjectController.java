package com.example.internship_ai_backend.controller;

import com.example.internship_ai_backend.dto.ProjectRequest;
import com.example.internship_ai_backend.dto.ProjectResponse;
import com.example.internship_ai_backend.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/students/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ResponseEntity<?> getProjects(@RequestParam String email) {
        try {
            List<ProjectResponse> projects = projectService.getProjectsByEmail(email);
            return ResponseEntity.ok(projects);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> addProject(@RequestParam String email, @RequestBody ProjectRequest request) {
        try {
            ProjectResponse project = projectService.addProject(email, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(project);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<?> updateProject(@PathVariable Integer projectId,
                                           @RequestParam String email,
                                           @RequestBody ProjectRequest request) {
        try {
            ProjectResponse project = projectService.updateProject(email, projectId, request);
            return ResponseEntity.ok(project);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> deleteProject(@PathVariable Integer projectId,
                                           @RequestParam String email) {
        try {
            projectService.deleteProject(email, projectId);
            return ResponseEntity.ok(Map.of("message", "Project deleted successfully"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}

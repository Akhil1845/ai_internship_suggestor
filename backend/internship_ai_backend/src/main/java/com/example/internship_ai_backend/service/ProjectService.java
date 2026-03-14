package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.dto.ProjectRequest;
import com.example.internship_ai_backend.dto.ProjectResponse;
import com.example.internship_ai_backend.entity.Project;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.repository.ProjectRepository;
import com.example.internship_ai_backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final StudentRepository studentRepository;

    public ProjectService(ProjectRepository projectRepository,
                          StudentRepository studentRepository) {
        this.projectRepository = projectRepository;
        this.studentRepository = studentRepository;
    }

    public List<ProjectResponse> getProjectsByEmail(String email) {
        Student student = findStudentByEmail(email);

        return projectRepository.findByStudentIdOrderByFeaturedDescUpdatedAtDesc(student.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectResponse addProject(String email, ProjectRequest request) {
        Student student = findStudentByEmail(email);
        validateProjectRequest(request);

        Project project = new Project();
        applyRequest(project, request);
        project.setStudent(student);

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional
    public ProjectResponse updateProject(String email, Integer projectId, ProjectRequest request) {
        Student student = findStudentByEmail(email);
        validateProjectRequest(request);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!project.getStudent().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Project does not belong to this student");
        }

        applyRequest(project, request);
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional
    public void deleteProject(String email, Integer projectId) {
        Student student = findStudentByEmail(email);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!project.getStudent().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Project does not belong to this student");
        }

        projectRepository.delete(project);
    }

    private void applyRequest(Project project, ProjectRequest request) {
        project.setTitle(clean(request.getTitle()));
        project.setDeployedLink(normalizeAndValidateUrl(clean(request.getDeployedLink()), true, "deployedLink"));
        project.setGithubLink(normalizeAndValidateUrl(clean(request.getGithubLink()), false, "githubLink"));
        project.setTechStack(clean(request.getTechStack()));
        project.setDescription(clean(request.getDescription()));
        project.setFeatured(request.isFeatured());
    }

    private void validateProjectRequest(ProjectRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (clean(request.getTitle()).isEmpty()) {
            throw new IllegalArgumentException("Project title is required");
        }

        if (clean(request.getDeployedLink()).isEmpty()) {
            throw new IllegalArgumentException("Deployed link is required");
        }
    }

    private String normalizeAndValidateUrl(String value, boolean required, String fieldName) {
        if (value.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return null;
        }

        String normalized = value;
        if (!normalized.matches("(?i)^https?://.*")) {
            normalized = "https://" + normalized;
        }

        try {
            URI parsed = URI.create(normalized);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException(fieldName + " must use http or https");
            }

            if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                throw new IllegalArgumentException(fieldName + " is not a valid URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " is not a valid URL");
        }

        return normalized;
    }

    private Student findStudentByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }

        Optional<Student> studentOpt = studentRepository.findByEmail(email.toLowerCase().trim());
        return studentOpt.orElseThrow(() -> new IllegalArgumentException("Student not found"));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setTitle(project.getTitle());
        response.setDeployedLink(project.getDeployedLink());
        response.setGithubLink(project.getGithubLink());
        response.setTechStack(project.getTechStack());
        response.setDescription(project.getDescription());
        response.setFeatured(project.isFeatured());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        return response;
    }
}

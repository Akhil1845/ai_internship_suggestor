package com.example.internship_ai_backend.repository;

import com.example.internship_ai_backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    List<Project> findByStudentIdOrderByFeaturedDescUpdatedAtDesc(Integer studentId);
}

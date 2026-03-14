package com.example.internship_ai_backend.repository;

import com.example.internship_ai_backend.entity.ContestPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestPreferenceRepository extends JpaRepository<ContestPreference, Integer> {

    List<ContestPreference> findByStudentEmail(String studentEmail);

    Optional<ContestPreference> findByStudentEmailAndPlatform(String studentEmail, String platform);
}

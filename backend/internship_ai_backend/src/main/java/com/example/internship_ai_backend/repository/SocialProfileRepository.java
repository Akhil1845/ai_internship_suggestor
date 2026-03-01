package com.example.internship_ai_backend.repository;

import com.example.internship_ai_backend.entity.SocialProfile;
import com.example.internship_ai_backend.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialProfileRepository extends JpaRepository<SocialProfile, Integer> {

    // Get all profiles of a student
    List<SocialProfile> findByStudentId(Integer studentId);

    // Get specific platform profile of a student
    Optional<SocialProfile> findByStudentIdAndPlatform(Integer studentId, Platform platform);

    // Delete specific platform profile
    void deleteByStudentIdAndPlatform(Integer studentId, Platform platform);
}
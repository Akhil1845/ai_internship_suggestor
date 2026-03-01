package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.entity.Platform;
import com.example.internship_ai_backend.entity.SocialProfile;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.repository.SocialProfileRepository;
import com.example.internship_ai_backend.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SocialProfileService {

    private final SocialProfileRepository socialProfileRepository;
    private final StudentRepository studentRepository;

    public SocialProfileService(SocialProfileRepository socialProfileRepository,
                                StudentRepository studentRepository) {
        this.socialProfileRepository = socialProfileRepository;
        this.studentRepository = studentRepository;
    }

    // ✅ Add or Update profile
    public SocialProfile saveOrUpdateProfile(Integer studentId, SocialProfile profile) {

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        profile.setStudent(student);

        return socialProfileRepository.save(profile);
    }

    // ✅ Get all profiles of student
    public List<SocialProfile> getAllProfiles(Integer studentId) {
        return socialProfileRepository.findByStudentId(studentId);
    }

    // ✅ Get specific platform profile
    public SocialProfile getProfileByPlatform(Integer studentId, Platform platform) {
        return socialProfileRepository
                .findByStudentIdAndPlatform(studentId, platform)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
    }

    // ✅ Delete specific profile
    public void deleteProfile(Integer studentId, Platform platform) {
        socialProfileRepository.deleteByStudentIdAndPlatform(studentId, platform);
    }
}
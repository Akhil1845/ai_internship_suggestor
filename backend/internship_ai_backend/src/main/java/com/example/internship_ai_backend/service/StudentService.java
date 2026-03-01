package com.example.internship_ai_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.internship_ai_backend.entity.Platform;
import com.example.internship_ai_backend.entity.SocialProfile;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.repository.SocialProfileRepository;
import com.example.internship_ai_backend.repository.StudentRepository;

import java.util.*;

@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SocialProfileRepository socialProfileRepository;

    // ===================== SIGNUP =====================
    public String signup(Student student) {

        if (student.getEmail() == null || student.getPassword() == null) {
            return "Email and Password are required!";
        }

        String email = student.getEmail().toLowerCase().trim();

        if (studentRepository.existsByEmail(email)) {
            return "Email already exists!";
        }

        student.setEmail(email);

        studentRepository.save(student);

        return "Signup successful!";
    }

    // ===================== LOGIN =====================
    public Optional<Student> login(String email, String password) {

        if (email == null || password == null) {
            return Optional.empty();
        }

        Optional<Student> optionalStudent =
                studentRepository.findByEmail(email.toLowerCase().trim());

        if (optionalStudent.isPresent()) {
            Student student = optionalStudent.get();

            if (student.getPassword().equals(password)) {
                return Optional.of(student);
            }
        }

        return Optional.empty();
    }

    // ===================== GET PROFILE =====================
    public Optional<Student> getStudentByEmail(String email) {

        if (email == null) {
            return Optional.empty();
        }

        return studentRepository.findByEmail(email.toLowerCase().trim());
    }

    // ===================== UPDATE PROFILE =====================
    public String updateProfile(String email, Student updatedData) {

        if (email == null) {
            return "Invalid email!";
        }

        Optional<Student> optionalStudent =
                studentRepository.findByEmail(email.toLowerCase().trim());

        if (optionalStudent.isEmpty()) {
            return "User not found!";
        }

        Student student = optionalStudent.get();

        student.setStudentClass(updatedData.getStudentClass());
        student.setSkills(updatedData.getSkills());
        student.setPreferredDomain(updatedData.getPreferredDomain());
        student.setQualification(updatedData.getQualification());
        student.setSecondaryEmail(updatedData.getSecondaryEmail());
        student.setContactNumber(updatedData.getContactNumber());
        student.setCollege(updatedData.getCollege());

        studentRepository.save(student);

        return "Profile updated successfully!";
    }

    // ===================== SAVE SOCIAL PROFILES =====================
    public String saveSocialProfiles(String email, List<Map<String, String>> profiles) {

        if (email == null) {
            return "Invalid email!";
        }

        Optional<Student> optionalStudent =
                studentRepository.findByEmail(email.toLowerCase().trim());

        if (optionalStudent.isEmpty()) {
            return "User not found!";
        }

        Student student = optionalStudent.get();

        List<SocialProfile> existingProfiles = socialProfileRepository.findByStudentId(student.getId());
        if (!existingProfiles.isEmpty()) {
            socialProfileRepository.deleteAll(existingProfiles);
        }

        if (profiles != null) {

            for (Map<String, String> profileData : profiles) {

                String platform = profileData.get("platform");
                String key = profileData.get("key");
                String url = profileData.get("url");

                if (platform == null || url == null ||
                        url.trim().isEmpty()) {
                    continue;
                }

                Platform platformEnum = parsePlatform(platform, key);
                if (platformEnum == null) {
                    continue;
                }

                SocialProfile socialProfile = new SocialProfile();
                socialProfile.setPlatform(platformEnum);
                socialProfile.setProfileUrl(url.trim());
                socialProfile.setUsername(extractUsername(url.trim()));
                socialProfile.setStudent(student);

                socialProfileRepository.save(socialProfile);
            }
        }

        return "Social profiles saved successfully!";
    }

    // ===================== GET SOCIAL PROFILES =====================
    public List<Map<String, String>> getSocialProfiles(String email) {

        if (email == null) {
            return new ArrayList<>();
        }

        Optional<Student> optionalStudent =
                studentRepository.findByEmail(email.toLowerCase().trim());

        if (optionalStudent.isEmpty()) {
            return new ArrayList<>();
        }

        Student student = optionalStudent.get();

        List<SocialProfile> profiles = socialProfileRepository.findByStudentId(student.getId());

        List<Map<String, String>> response = new ArrayList<>();

        for (SocialProfile profile : profiles) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("platform", toDisplayPlatform(profile.getPlatform()));
            map.put("key", profile.getPlatform().name().toLowerCase());
            map.put("url", profile.getProfileUrl());
            response.add(map);
        }

        return response;
    }

    private Platform parsePlatform(String platform, String key) {
        String normalized = "";

        if (platform != null && !platform.trim().isEmpty()) {
            normalized = platform.trim().toUpperCase();
        } else if (key != null && !key.trim().isEmpty()) {
            normalized = key.trim().toUpperCase();
        }

        normalized = normalized.replace(" ", "");

        switch (normalized) {
            case "LINKEDIN": return Platform.LINKEDIN;
            case "GITHUB": return Platform.GITHUB;
            case "LEETCODE": return Platform.LEETCODE;
            case "CODECHEF": return Platform.CODECHEF;
            case "HACKERRANK": return Platform.HACKERRANK;
            default: return null;
        }
    }

    private String toDisplayPlatform(Platform platform) {
        switch (platform) {
            case LINKEDIN: return "LinkedIn";
            case GITHUB: return "GitHub";
            case LEETCODE: return "LeetCode";
            case CODECHEF: return "CodeChef";
            case HACKERRANK: return "HackerRank";
            default: return platform.name();
        }
    }

    private String extractUsername(String profileUrl) {
        if (profileUrl == null) {
            return "";
        }

        String cleaned = profileUrl.trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == cleaned.length() - 1) {
            return cleaned;
        }

        return cleaned.substring(lastSlash + 1);
    }
}
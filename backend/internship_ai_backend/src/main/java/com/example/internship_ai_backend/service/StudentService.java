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

        if (student.getEmail() == null) {
            return "Email is required!";
        }

        // For local signup, password is required
        if ("local".equals(student.getAuthProvider()) || student.getAuthProvider() == null) {
            if (student.getPassword() == null) {
                return "Password is required!";
            }
            student.setAuthProvider("local");
        }

        String email = student.getEmail().toLowerCase().trim();

        if (studentRepository.existsByEmail(email)) {
            return "Email already exists!";
        }

        // Check username uniqueness
        if (student.getUsername() != null && !student.getUsername().trim().isEmpty()) {
            String username = student.getUsername().trim();
            if (studentRepository.existsByUsername(username)) {
                return "Username already exists!";
            }
            student.setUsername(username);
        }

        student.setEmail(email);

        studentRepository.save(student);

        return "Signup successful!";
    }

    // ===================== LOGIN =====================
    public Optional<Student> login(String usernameOrEmail, String password) {

        if (usernameOrEmail == null || password == null) {
            return Optional.empty();
        }

        String input = usernameOrEmail.toLowerCase().trim();
        Optional<Student> optionalStudent;

        // Try email first
        optionalStudent = studentRepository.findByEmail(input);

        // If not found by email, try username
        if (optionalStudent.isEmpty()) {
            optionalStudent = studentRepository.findByUsername(input);
        }

        if (optionalStudent.isPresent()) {
            Student student = optionalStudent.get();

            // Only allow password login for local auth
            if ("local".equals(student.getAuthProvider()) && 
                student.getPassword() != null && 
                student.getPassword().equals(password)) {
                return Optional.of(student);
            }
        }

        return Optional.empty();
    }

    // ===================== OAUTH LOGIN/SIGNUP =====================
    public Student handleOAuthUser(String email, String name, String googleId) {
        
        // Check if user already exists with this Google ID
        Optional<Student> existingByGoogleId = studentRepository.findByGoogleId(googleId);
        if (existingByGoogleId.isPresent()) {
            return existingByGoogleId.get();
        }

        // Check if user exists with this email (link accounts)
        Optional<Student> existingByEmail = studentRepository.findByEmail(email.toLowerCase().trim());
        if (existingByEmail.isPresent()) {
            Student student = existingByEmail.get();
            student.setGoogleId(googleId);
            student.setAuthProvider("google");
            return studentRepository.save(student);
        }

        // Create new user
        Student newStudent = new Student();
        newStudent.setEmail(email.toLowerCase().trim());
        newStudent.setName(name);
        newStudent.setGoogleId(googleId);
        newStudent.setAuthProvider("google");
        // No password needed for OAuth users
        
        return studentRepository.save(newStudent);
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
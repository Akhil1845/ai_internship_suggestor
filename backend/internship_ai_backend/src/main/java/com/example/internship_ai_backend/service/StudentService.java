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

    private static final String OAUTH_PASSWORD_PREFIX = "oauth_google_";

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
            if (student.getUsername() == null || student.getUsername().trim().isEmpty()) {
                return "Username is required!";
            }
            student.setAuthProvider("local");
        }

        String email = student.getEmail().toLowerCase().trim();

        if (studentRepository.existsByEmail(email)) {
            return "Email already exists!";
        }

        // Check username uniqueness
        if (student.getUsername() != null && !student.getUsername().trim().isEmpty()) {
            String username = student.getUsername().trim().toLowerCase();
            if (studentRepository.existsByUsername(username)) {
                return "Username is already taken. Please choose another one.";
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

            // Allow password login for any user who has a stored password.
            if (student.getPassword() != null && student.getPassword().equals(password)) {
                return Optional.of(student);
            }
        }

        return Optional.empty();
    }

    // ===================== OAUTH LOGIN/SIGNUP =====================
    public Student handleOAuthUser(String email, String name, String googleId) {
        String normalizedEmail = email == null ? "" : email.toLowerCase().trim();
        String resolvedName = (name == null || name.trim().isEmpty())
                ? (normalizedEmail.contains("@") ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : "Google User")
                : name.trim();
        String oauthPassword = buildOAuthPlaceholderPassword(googleId, normalizedEmail);
        
        // Check if user already exists with this Google ID
        Optional<Student> existingByGoogleId = studentRepository.findByGoogleId(googleId);
        if (existingByGoogleId.isPresent()) {
            return existingByGoogleId.get();
        }

        // Check if user exists with this email (link accounts)
        Optional<Student> existingByEmail = studentRepository.findByEmail(normalizedEmail);
        if (existingByEmail.isPresent()) {
            Student student = existingByEmail.get();
            student.setGoogleId(googleId);
            if (student.getName() == null || student.getName().trim().isEmpty()) {
                student.setName(resolvedName);
            }
            if (student.getAuthProvider() == null || student.getAuthProvider().trim().isEmpty()) {
                student.setAuthProvider("google");
            }
            if (student.getPassword() == null || student.getPassword().trim().isEmpty()) {
                student.setPassword(oauthPassword);
            }
            return studentRepository.save(student);
        }

        // Create new user
        Student newStudent = new Student();
        newStudent.setEmail(normalizedEmail);
        newStudent.setName(resolvedName);
        newStudent.setGoogleId(googleId);
        newStudent.setAuthProvider("google");
        // Keep a non-null placeholder to satisfy DB schemas that require password.
        newStudent.setPassword(oauthPassword);
        
        return studentRepository.save(newStudent);
    }

    private String buildOAuthPlaceholderPassword(String googleId, String email) {
        String idPart = (googleId == null || googleId.isBlank()) ? "id" : googleId.trim();
        String emailPart = (email == null || email.isBlank()) ? "user" : email.replaceAll("[^a-zA-Z0-9]", "");
        return OAUTH_PASSWORD_PREFIX + idPart + "_" + emailPart;
    }

    // ===================== COMPLETE GOOGLE SIGNUP =====================
    public String completeGoogleSignup(Integer studentId, String username, String password) {

        if (studentId == null) {
            return "Invalid user id!";
        }
        if (username == null || username.trim().isEmpty()) {
            return "Username is required!";
        }
        if (password == null || password.trim().isEmpty()) {
            return "Password is required!";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters";
        }

        Optional<Student> optionalStudent = studentRepository.findById(studentId);
        if (optionalStudent.isEmpty()) {
            return "User not found!";
        }

        Student student = optionalStudent.get();
        String normalizedUsername = username.trim().toLowerCase();

        Optional<Student> existingByUsername = studentRepository.findByUsername(normalizedUsername);
        if (existingByUsername.isPresent() && !existingByUsername.get().getId().equals(studentId)) {
            return "Username is already taken. Please choose another one.";
        }

        student.setUsername(normalizedUsername);
        student.setPassword(password);
        if (student.getAuthProvider() == null || student.getAuthProvider().trim().isEmpty()) {
            student.setAuthProvider("google");
        }

        studentRepository.save(student);
        return "Google signup completed!";
    }

    // ===================== FORGOT PASSWORD =====================
    public String verifyPasswordResetIdentity(String email, String identifier) {

        if (email == null || email.trim().isEmpty()) {
            return "Email is required!";
        }

        if (identifier == null || identifier.trim().isEmpty()) {
            return "Username or full name is required!";
        }

        String normalizedEmail = email.toLowerCase().trim();
        String normalizedIdentifier = identifier.trim().replaceAll("\\s+", " ").toLowerCase();

        Optional<Student> optionalStudent = studentRepository.findByEmail(normalizedEmail);

        if (optionalStudent.isEmpty()) {
            return "User not found!";
        }

        Student student = optionalStudent.get();

        String studentUsername = student.getUsername() == null ? "" : student.getUsername().trim().toLowerCase();
        String studentName = student.getName() == null
                ? ""
                : student.getName().trim().replaceAll("\\s+", " ").toLowerCase();

        boolean verified = studentUsername.equals(normalizedIdentifier)
                || studentName.equals(normalizedIdentifier);

        if (!verified) {
            return "Verification failed! Username or full name does not match this email.";
        }

        return "Identity verified!";
    }

    public String resetPassword(String email, String identifier, String newPassword) {

        String verifyResult = verifyPasswordResetIdentity(email, identifier);
        if (!verifyResult.equals("Identity verified!")) {
            return verifyResult;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            return "New password is required!";
        }

        if (newPassword.length() < 8) {
            return "Password must be at least 8 characters";
        }

        Student student = studentRepository.findByEmail(email.toLowerCase().trim()).orElseThrow();

        student.setPassword(newPassword);
        studentRepository.save(student);

        return "Password reset successful!";
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

        if (updatedData.getName() != null && !updatedData.getName().trim().isEmpty()) {
            student.setName(updatedData.getName().trim());
        }

        if (updatedData.getUsername() != null) {
            String normalizedUsername = updatedData.getUsername().trim().toLowerCase();

            if (normalizedUsername.isEmpty()) {
                return "Username cannot be empty!";
            }

            if (normalizedUsername.length() < 3) {
                return "Username must be at least 3 characters";
            }

            Optional<Student> existingByUsername = studentRepository.findByUsername(normalizedUsername);
            if (existingByUsername.isPresent() && !existingByUsername.get().getId().equals(student.getId())) {
                return "Username is already taken. Please choose another one.";
            }

            student.setUsername(normalizedUsername);
        }

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
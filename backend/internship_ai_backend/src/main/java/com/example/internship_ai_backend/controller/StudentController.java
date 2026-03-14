package com.example.internship_ai_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.internship_ai_backend.dto.ResumeRecommendationsResponse;
import com.example.internship_ai_backend.dto.SocialAnalyticsResponse;
import com.example.internship_ai_backend.dto.MatchedInternshipDTO;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.service.InternshipRecommendationService;
import com.example.internship_ai_backend.service.InternshipMatchingService;
import com.example.internship_ai_backend.service.SocialAnalyticsService;
import com.example.internship_ai_backend.service.StudentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private SocialAnalyticsService socialAnalyticsService;

    @Autowired
    private InternshipRecommendationService internshipRecommendationService;

    @Autowired
    private InternshipMatchingService internshipMatchingService;

    // ===================== SIGNUP =====================
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody Student student) {

        String result = studentService.signup(student);

        if (result.equals("Signup successful!")) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    // ===================== LOGIN =====================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Student student) {

        Optional<Student> loggedIn =
                studentService.login(student.getEmail(), student.getPassword());

        if (loggedIn.isPresent()) {
            return ResponseEntity.ok(loggedIn.get());
        }

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Invalid credentials!");
    }

        // ===================== FORGOT PASSWORD VERIFY =====================
        @PostMapping("/forgot-password/verify")
        public ResponseEntity<String> verifyForgotPassword(@RequestBody Map<String, String> payload) {

                String email = payload.get("email");
                String identifier = payload.get("identifier");

                String result = studentService.verifyPasswordResetIdentity(email, identifier);

                if (result.equals("Identity verified!")) {
                        return ResponseEntity.ok(result);
                }

                HttpStatus status;
                if (result.equals("User not found!")) {
                        status = HttpStatus.NOT_FOUND;
                } else if (result.startsWith("Verification failed!")) {
                        status = HttpStatus.UNAUTHORIZED;
                } else {
                        status = HttpStatus.BAD_REQUEST;
                }

                return ResponseEntity
                                .status(status)
                                .body(result);
        }

        // ===================== FORGOT PASSWORD RESET =====================
        @PostMapping("/forgot-password")
        public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> payload) {

                String email = payload.get("email");
                String identifier = payload.get("identifier");
                String newPassword = payload.get("newPassword");

                String result = studentService.resetPassword(email, identifier, newPassword);

                if (result.equals("Password reset successful!")) {
                        return ResponseEntity.ok(result);
                }

                HttpStatus status;
                if (result.equals("User not found!")) {
                        status = HttpStatus.NOT_FOUND;
                } else if (result.startsWith("Verification failed!")) {
                        status = HttpStatus.UNAUTHORIZED;
                } else {
                        status = HttpStatus.BAD_REQUEST;
                }

                return ResponseEntity
                                .status(status)
                                .body(result);
        }

    // ===================== GET STUDENT PROFILE =====================
    @GetMapping("/profile")
    public ResponseEntity<?> getStudent(@RequestParam String email) {

        Optional<Student> student =
                studentService.getStudentByEmail(email);

        if (student.isPresent()) {
            return ResponseEntity.ok(student.get());
        }

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("User not found!");
    }

    // ===================== UPDATE PROFILE =====================
    @PutMapping("/profile")
    public ResponseEntity<String> updateProfile(
            @RequestParam String email,
            @RequestBody Student updatedData) {

        String result =
                studentService.updateProfile(email, updatedData);

        if (result.equals("Profile updated successfully!")) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    // ===================== SAVE SOCIAL PROFILES =====================
    @PostMapping("/social")
    public ResponseEntity<String> saveSocialProfiles(
            @RequestParam String email,
            @RequestBody Map<String, List<Map<String, String>>> payload) {

        List<Map<String, String>> socialProfiles =
                payload.getOrDefault("socialProfiles", new ArrayList<>());

        String result =
                studentService.saveSocialProfiles(email, socialProfiles);

        if (result.equals("Social profiles saved successfully!")) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(result);
    }

    // ===================== GET SOCIAL PROFILES =====================
    @GetMapping("/social")
    public ResponseEntity<List<Map<String, String>>> getSocialProfiles(
            @RequestParam String email) {

        List<Map<String, String>> profiles =
                studentService.getSocialProfiles(email);

        return ResponseEntity.ok(profiles);
    }

    // ===================== GET SOCIAL ANALYTICS =====================
    @GetMapping("/social/analytics")
    public ResponseEntity<SocialAnalyticsResponse> getSocialAnalytics(
            @RequestParam String email,
            @RequestParam String platform) {

        SocialAnalyticsResponse analytics =
                socialAnalyticsService.getAnalytics(email, platform);

        return ResponseEntity.ok(analytics);
    }

        // ===================== RESUME RECOMMENDATIONS =====================
        @PostMapping(value = "/resume/recommendations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ResumeRecommendationsResponse> getResumeRecommendations(
                        @RequestParam String email,
                        @RequestPart("resume") MultipartFile resume) {

                ResumeRecommendationsResponse recommendations =
                                internshipRecommendationService.getRecommendationsFromResume(email, resume);

                return ResponseEntity.ok(recommendations);
        }

        // ===================== MATCH INTERNSHIPS =====================
        @GetMapping("/internships/match")
        public ResponseEntity<List<MatchedInternshipDTO>> getMatchedInternships(
                        @RequestParam String email) {

                List<MatchedInternshipDTO> matchedInternships =
                                internshipMatchingService.matchInternships(email);

                return ResponseEntity.ok(matchedInternships);
        }

        // ===================== GET ALL INTERNSHIPS =====================
        @GetMapping("/internships/all")
        public ResponseEntity<List<MatchedInternshipDTO>> getAllInternships() {

                List<MatchedInternshipDTO> allInternships =
                                internshipMatchingService.getAllInternships();

                return ResponseEntity.ok(allInternships);
        }
}
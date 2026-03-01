package com.example.internship_ai_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.internship_ai_backend.entity.Student;
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
}
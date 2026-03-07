package com.example.internship_ai_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.service.GoogleOAuthService;
import com.example.internship_ai_backend.service.StudentService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class OAuthController {

    @Autowired
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private StudentService studentService;

    /**
     * Handle Google OAuth callback
     * This endpoint receives the authorization code from Google
     */
    @GetMapping("/google/callback")
    public RedirectView handleGoogleCallback(@RequestParam String code,
                                             @RequestParam(required = false) String state) {
        
        try {
            String flowMode = (state == null || state.trim().isEmpty()) ? "login" : state.trim().toLowerCase();
            String redirectUri = "http://localhost:8089/api/auth/google/callback";
            
            // Exchange code for access token
            String accessToken = googleOAuthService.exchangeCodeForToken(code, redirectUri);
            
            // Get user info from Google
            Map<String, String> userInfo = googleOAuthService.getUserInfo(accessToken);
            
            // Handle OAuth user (create or login)
            Student student = studentService.handleOAuthUser(
                userInfo.get("email"),
                userInfo.get("name"),
                userInfo.get("id")
            );
            
            RedirectView redirectView = new RedirectView();

                boolean needsSetup = "signup".equals(flowMode) &&
                    (student.getUsername() == null || student.getUsername().trim().isEmpty() ||
                        student.getPassword() == null || student.getPassword().trim().isEmpty());

                String encodedEmail = URLEncoder.encode(student.getEmail(), StandardCharsets.UTF_8);
                String encodedName = URLEncoder.encode(student.getName() == null ? "" : student.getName(), StandardCharsets.UTF_8);

                if (needsSetup) {
                redirectView.setUrl("http://localhost:5500/auth-callback.html?setup=true&userId=" +
                    student.getId() + "&email=" + encodedEmail + "&name=" + encodedName);
                } else {
                redirectView.setUrl("http://localhost:5500/auth-callback.html?success=true&userId=" +
                    student.getId() + "&email=" + encodedEmail + "&name=" + encodedName);
                }
            
            return redirectView;
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // Redirect to frontend with error
            RedirectView redirectView = new RedirectView();
            String encodedError = URLEncoder.encode(e.getMessage() == null ? "OAuth failed" : e.getMessage(), StandardCharsets.UTF_8);
            redirectView.setUrl("http://localhost:5500/auth-callback.html?success=false&error=" + encodedError);
            
            return redirectView;
        }
    }

    /**
     * Endpoint for testing OAuth flow
     */
    @GetMapping("/google/login-url")
    public ResponseEntity<?> getGoogleLoginUrl() {
        
        String clientId = "489484268154-mllmp32m1cpk8db03sfcq8bstl01ogpt.apps.googleusercontent.com";
        String redirectUri = "http://localhost:8089/api/auth/google/callback";
        String scope = "email profile";
        String state = "random_state_string"; // Should be random for security
        
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=" + scope +
                "&state=" + state;
        
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @PostMapping("/google/complete-signup")
    public ResponseEntity<?> completeGoogleSignup(@RequestBody Map<String, String> payload) {

        Integer userId;
        try {
            userId = Integer.parseInt(payload.get("userId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid user id!");
        }

        String result = studentService.completeGoogleSignup(
                userId,
                payload.get("username"),
                payload.get("password")
        );

        if ("Google signup completed!".equals(result)) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.badRequest().body(result);
    }
}

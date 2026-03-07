package com.example.internship_ai_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.service.GoogleOAuthService;
import com.example.internship_ai_backend.service.StudentService;

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
    public RedirectView handleGoogleCallback(@RequestParam String code, @RequestParam String state) {
        
        try {
            // Decode the redirect URI from the state parameter
            String redirectUri = "http://localhost:5500/auth-callback.html";
            
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
            
            // Redirect to frontend with user data
            RedirectView redirectView = new RedirectView();
            redirectView.setUrl("http://localhost:5500/auth-callback.html?success=true&userId=" + 
                student.getId() + "&email=" + student.getEmail() + "&name=" + student.getName());
            
            return redirectView;
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // Redirect to frontend with error
            RedirectView redirectView = new RedirectView();
            redirectView.setUrl("http://localhost:5500/auth-callback.html?success=false&error=" + 
                e.getMessage());
            
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
}

package com.example.internship_ai_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.frontend.base-url:http://localhost:5500/frontend}")
    private String frontendBaseUrl;

    private String buildFrontendUrl(String pathAndQuery) {
        String baseUrl = frontendBaseUrl == null ? "http://localhost:5500" : frontendBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + pathAndQuery;
    }

    /**
     * Handle Google OAuth callback
     * This endpoint receives the authorization code from Google
     */
    @GetMapping("/google/callback")
    public RedirectView handleGoogleCallback(@RequestParam String code,
                                             @RequestParam(required = false) String state) {
        
        try {
            String flowMode = (state == null || state.trim().isEmpty()) ? "login" : state.trim().toLowerCase();
            String redirectUri = googleOAuthService.getRedirectUri();
            
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
                redirectView.setUrl(buildFrontendUrl("/user_login.html?oauthSetup=true&userId=") +
                    student.getId() + "&email=" + encodedEmail + "&name=" + encodedName);
                } else {
                redirectView.setUrl(buildFrontendUrl("/user_login.html?oauthSuccess=true&userId=") +
                    student.getId() + "&email=" + encodedEmail + "&name=" + encodedName);
                }
            
            return redirectView;
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // Redirect to frontend with error
            RedirectView redirectView = new RedirectView();
            String safeMessage = "Google authentication failed. Please try again.";
            String rawMessage = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            if (rawMessage.contains("client secret is missing")) {
                safeMessage = "Google OAuth is not configured on the server.";
            } else if (rawMessage.contains("duplicate") || rawMessage.contains("already exists")) {
                safeMessage = "Account already exists. Try logging in directly.";
            }

            String encodedError = URLEncoder.encode(safeMessage, StandardCharsets.UTF_8);
            redirectView.setUrl(buildFrontendUrl("/user_login.html?oauthError=") + encodedError);
            
            return redirectView;
        }
    }

    /**
     * Endpoint for testing OAuth flow
     */
    @GetMapping("/google/login-url")
    public ResponseEntity<?> getGoogleLoginUrl(@RequestParam(defaultValue = "login") String flow) {
        String normalizedFlow = "signup".equalsIgnoreCase(flow) ? "signup" : "login";
        String authUrl = googleOAuthService.buildAuthorizationUrl(normalizedFlow);
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

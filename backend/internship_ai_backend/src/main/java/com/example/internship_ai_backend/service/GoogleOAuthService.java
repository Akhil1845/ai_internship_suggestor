package com.example.internship_ai_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class GoogleOAuthService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    @Value("${app.oauth.google.client-id}")
    private String clientId;

    @Value("${app.oauth.google.client-secret:}")
    private String clientSecret;

    @Value("${app.oauth.google.redirect-uri}")
    private String redirectUri;

    @Value("${app.oauth.google.scope:email profile}")
    private String scope;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getRedirectUri() {
        return redirectUri;
    }

    public String buildAuthorizationUrl(String state) {
        String safeState = (state == null || state.isBlank()) ? "login" : state.trim().toLowerCase();
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + URLEncoder.encode(safeState, StandardCharsets.UTF_8);
    }

    private void validateOAuthConfig() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google OAuth client id is missing");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth client secret is missing");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalStateException("Google OAuth redirect URI is missing");
        }
    }

    /**
     * Exchange authorization code for access token
     */
    public String exchangeCodeForToken(String code, String redirectUri) throws Exception {
        validateOAuthConfig();
        
        String requestBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to exchange code for token: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        return json.get("access_token").asText();
    }

    /**
     * Get user info from Google using access token
     */
    public Map<String, String> getUserInfo(String accessToken) throws Exception {
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USERINFO_URL))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get user info: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("id", json.get("id").asText());
        userInfo.put("email", json.get("email").asText());
        userInfo.put("name", json.get("name").asText());
        userInfo.put("picture", json.has("picture") ? json.get("picture").asText() : null);
        
        return userInfo;
    }
}

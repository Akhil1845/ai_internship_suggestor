package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.dto.JobRecommendationDto;
import com.example.internship_ai_backend.dto.ResumeRecommendationsResponse;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class InternshipRecommendationService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${automation.internships.endpoint:http://localhost:5678/webhook/internship-recommendations}")
    private String automationEndpoint;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    public ResumeRecommendationsResponse getRecommendationsFromResume(String email, MultipartFile resumeFile) {
        ResumeRecommendationsResponse response = new ResumeRecommendationsResponse();

        if (email == null || email.isBlank()) {
            response.setMessage("Invalid email.");
            return response;
        }

        if (resumeFile == null || resumeFile.isEmpty()) {
            response.setMessage("Resume file is required.");
            return response;
        }

        Optional<Student> optionalStudent = studentRepository.findByEmail(email.toLowerCase().trim());
        if (optionalStudent.isEmpty()) {
            response.setMessage("Student not found.");
            return response;
        }

        Student student = optionalStudent.get();

        try {
            byte[] fileBytes = resumeFile.getBytes();
            String fileName = resumeFile.getOriginalFilename() == null ? "resume" : resumeFile.getOriginalFilename();
            String fileType = resumeFile.getContentType() == null ? "application/octet-stream" : resumeFile.getContentType();
            String resumeText = extractTextSafely(fileType, fileBytes);

            List<String> extractedKeywords = collectKeywords(student, resumeText, fileName);
            response.setExtractedKeywords(extractedKeywords);

            JsonNode automationResult = callAutomation(email, student, fileName, fileType, fileBytes, resumeText, extractedKeywords);
            List<JobRecommendationDto> recommendations = buildRecommendations(automationResult, extractedKeywords);

            response.setRecommendations(recommendations);
            if (recommendations.isEmpty()) {
                response.setMessage("No matching internships/jobs found from automation data.");
            } else {
                response.setMessage("Recommended opportunities based on your resume.");
            }
        } catch (Exception exception) {
            response.setMessage("Failed to process resume recommendations: " + exception.getMessage());
        }

        return response;
    }

    private JsonNode callAutomation(String email,
                                    Student student,
                                    String fileName,
                                    String fileType,
                                    byte[] fileBytes,
                                    String resumeText,
                                    List<String> extractedKeywords) throws Exception {

        Map<String, Object> payload = Map.of(
                "email", email,
                "studentName", safe(student.getName()),
                "studentSkills", safe(student.getSkills()),
                "preferredDomain", safe(student.getPreferredDomain()),
                "resumeFileName", fileName,
                "resumeFileType", fileType,
                "resumeBase64", Base64.getEncoder().encodeToString(fileBytes),
                "resumeText", resumeText,
                "keywords", extractedKeywords
        );

        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(automationEndpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Automation endpoint returned HTTP " + response.statusCode());
        }

        return objectMapper.readTree(response.body());
    }

    private List<JobRecommendationDto> buildRecommendations(JsonNode root, List<String> keywords) {
        List<JobRecommendationDto> result = new ArrayList<>();
        List<JsonNode> items = extractJobNodes(root);

        for (JsonNode item : items) {
            JobRecommendationDto dto = new JobRecommendationDto();
            dto.setTitle(readText(item, "title", "jobTitle", "role", "position"));
            dto.setCompany(readText(item, "company", "companyName", "organization"));
            dto.setLocation(readText(item, "location", "city", "place"));
            dto.setType(readText(item, "type", "employmentType", "internshipType"));
            dto.setSource(readText(item, "source", "platform", "website"));
            dto.setUrl(readText(item, "url", "applyUrl", "link", "jobUrl"));

            String searchable = (
                    safe(dto.getTitle()) + " " +
                    safe(dto.getCompany()) + " " +
                    safe(dto.getLocation()) + " " +
                    safe(dto.getType()) + " " +
                    safe(dto.getSource()) + " " +
                    readText(item, "description", "summary", "skills", "requirements")
            ).toLowerCase(Locale.ENGLISH);

            List<String> matched = new ArrayList<>();
            for (String keyword : keywords) {
                String normalized = keyword.toLowerCase(Locale.ENGLISH);
                if (!normalized.isBlank() && searchable.contains(normalized)) {
                    matched.add(keyword);
                }
            }

            dto.setMatchedSkills(matched);
            int score = Math.min(100, matched.size() * 12);
            dto.setMatchScore(score);

            if (!safe(dto.getTitle()).isBlank() || !safe(dto.getCompany()).isBlank()) {
                result.add(dto);
            }
        }

        result.sort(Comparator.comparingInt(JobRecommendationDto::getMatchScore).reversed());
        if (result.size() > 20) {
            return new ArrayList<>(result.subList(0, 20));
        }
        return result;
    }

    private List<JsonNode> extractJobNodes(JsonNode root) {
        List<JsonNode> nodes = new ArrayList<>();
        if (root == null || root.isNull()) {
            return nodes;
        }

        if (root.isArray()) {
            for (JsonNode node : root) {
                if (node.has("json") && node.get("json").isObject()) {
                    nodes.add(node.get("json"));
                } else {
                    nodes.add(node);
                }
            }
            return nodes;
        }

        JsonNode jobs = firstExisting(root, "jobs", "internships", "recommendations", "data", "items");
        if (jobs != null && jobs.isArray()) {
            for (JsonNode node : jobs) {
                if (node.has("json") && node.get("json").isObject()) {
                    nodes.add(node.get("json"));
                } else {
                    nodes.add(node);
                }
            }
            return nodes;
        }

        if (root.has("json") && root.get("json").isObject()) {
            JsonNode nested = root.get("json");
            JsonNode nestedJobs = firstExisting(nested, "jobs", "internships", "recommendations", "data", "items");
            if (nestedJobs != null && nestedJobs.isArray()) {
                nestedJobs.forEach(nodes::add);
                return nodes;
            }
        }

        if (root.isObject()) {
            nodes.add(root);
        }
        return nodes;
    }

    private JsonNode firstExisting(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                return node.get(fieldName);
            }
        }
        return null;
    }

    private String readText(JsonNode node, String... names) {
        if (node == null) {
            return "";
        }
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name).asText("").trim();
            }
        }
        return "";
    }

    private List<String> collectKeywords(Student student, String resumeText, String fileName) {
        Set<String> keywords = new LinkedHashSet<>();

        addTokens(keywords, safe(student.getSkills()));
        addTokens(keywords, safe(student.getPreferredDomain()));
        addTokens(keywords, safe(fileName).replace('.', ' '));
        addTokens(keywords, safe(resumeText));

        return new ArrayList<>(keywords);
    }

    private void addTokens(Set<String> keywords, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        String[] parts = text
                .replaceAll("[^a-zA-Z0-9+#.\\- ]", " ")
                .toLowerCase(Locale.ENGLISH)
                .split("[\\s,;/|]+");

        for (String part : parts) {
            String token = part.trim();
            if (token.length() < 2 || token.length() > 24) {
                continue;
            }
            if (isNoise(token)) {
                continue;
            }
            keywords.add(token);
            if (keywords.size() >= 80) {
                break;
            }
        }
    }

    private boolean isNoise(String token) {
        return token.equals("the") || token.equals("and") || token.equals("for") || token.equals("with")
                || token.equals("this") || token.equals("that") || token.equals("from") || token.equals("you")
                || token.equals("your") || token.equals("www") || token.equals("http") || token.equals("https")
                || token.equals("com") || token.equals("resume") || token.equals("curriculum") || token.equals("vitae");
    }

    private String extractTextSafely(String contentType, byte[] fileBytes) {
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ENGLISH);
        if (lowerType.contains("text") || lowerType.contains("json") || lowerType.contains("xml")) {
            return new String(fileBytes, StandardCharsets.UTF_8);
        }

        String decoded = new String(fileBytes, StandardCharsets.UTF_8)
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (decoded.length() > 6000) {
            return decoded.substring(0, 6000);
        }

        return decoded;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

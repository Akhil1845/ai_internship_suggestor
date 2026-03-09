package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.dto.JobRecommendationDto;
import com.example.internship_ai_backend.dto.ResumeRecommendationsResponse;
import com.example.internship_ai_backend.entity.Internship;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.repository.InternshipRepository;
import com.example.internship_ai_backend.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class InternshipRecommendationService {

    private static final Set<String> NOISE_TOKENS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "into", "is", "it", "of", "on", "or", "that", "the", "their", "them", "these", "this", "those", "to", "with",
            "about", "across", "after", "before", "during", "over", "under", "such", "using", "used", "use",
            "you", "your", "we", "our", "i", "me", "my", "have", "has", "had", "will", "can",
            "experience", "experienced", "project", "projects", "skill", "skills", "resume", "curriculum", "vitae",
            "www", "http", "https", "com",
            "platform", "multiple", "seeking", "motivated", "technical", "development", "learning", "sharing", "document", "client",
            "opportunity", "opportunities", "role", "position", "team", "work", "working"
    );

            private static final Set<String> RESUME_SECTION_HINTS = Set.of(
                "education", "experience", "projects", "skills", "certifications",
                "objective", "summary", "internship", "achievements", "profile"
            );

            private static final Set<String> TECH_KEYWORD_HINTS = Set.of(
                "java", "python", "javascript", "typescript", "react", "angular", "vue", "node", "spring", "sql", "mysql", "mongodb", "postgresql",
                "html", "css", "bootstrap", "tailwind", "git", "github", "docker", "kubernetes", "aws", "azure", "gcp", "linux", "rest", "api",
                "ml", "ai", "tensorflow", "pytorch", "data", "analytics", "devops", "c", "cpp", "csharp", "go", "rust", "php"
            );

            private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
            private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(?\\d{3,5}\\)?[\\s-]?)?\\d{3,5}[\\s-]?\\d{3,5}");

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InternshipRepository internshipRepository;

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

        if (!isSupportedResumeFile(resumeFile)) {
            response.setMessage("Unsupported resume format. Upload PDF, DOC, DOCX, or TXT.");
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
            String resumeText = extractTextSafely(fileName, fileType, fileBytes);

            if (!looksLikeResume(resumeText)) {
                response.setRecommendations(new ArrayList<>());
                response.setMessage("❌ Uploaded file does not appear to be a valid resume. Please upload a resume that includes sections like Education, Experience, Skills, and your contact information.");
                return response;
            }

            List<String> extractedKeywords = collectKeywords(resumeText);
            response.setExtractedKeywords(extractedKeywords);

            // Prevent random/empty PDFs from producing generic recommendations.
            if (extractedKeywords.size() < 4) {
                response.setRecommendations(new ArrayList<>());
                response.setMessage("❌ Could not extract enough content from the resume. Please upload a text-based resume PDF (not an image/scanned PDF) with your skills and projects.");
                return response;
            }

            if (!hasEnoughTechnicalSignals(extractedKeywords)) {
                response.setRecommendations(new ArrayList<>());
                response.setMessage("❌ Could not detect technical skills in the uploaded resume. Please ensure your resume includes relevant technical skills for internship matching.");
                return response;
            }

            List<JobRecommendationDto> recommendations;
            try {
                JsonNode automationResult = callAutomation(email, student, fileName, fileType, fileBytes, resumeText, extractedKeywords);
                recommendations = buildRecommendations(automationResult, extractedKeywords);
            } catch (Exception automationError) {
                // Fallback to local DB matching when automation webhook is unavailable.
                recommendations = buildLocalRecommendations(extractedKeywords);
            }

            response.setRecommendations(recommendations);
            if (recommendations.isEmpty()) {
                response.setMessage("No matching internships found for the uploaded resume.");
            } else {
                response.setMessage("Recommended opportunities based on your resume.");
            }
        } catch (Exception exception) {
            response.setMessage("Failed to process resume recommendations: " + summarizeException(exception));
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
        Set<String> resumeKeywordSet = toKeywordSet(keywords);

        for (JsonNode item : items) {
            JobRecommendationDto dto = new JobRecommendationDto();
            dto.setTitle(readText(item, "title", "jobTitle", "role", "position"));
            dto.setCompany(readText(item, "company", "companyName", "organization"));
            dto.setLocation(readText(item, "location", "city", "place"));
            dto.setType(readText(item, "type", "employmentType", "internshipType"));
            dto.setSource(readText(item, "source", "platform", "website"));
            dto.setUrl(readText(item, "url", "applyUrl", "link", "jobUrl"));

            Set<String> internshipKeywords = extractInternshipKeywords(item);
            Set<String> requiredKeywords = extractTokensFromText(readText(item, "requiredSkills", "skills", "requirements", "mustHave", "mustHaveSkills"));
            List<String> matched = orderedMatchedKeywords(keywords, internshipKeywords);

            if (matched.size() < 2 || internshipKeywords.isEmpty()) {
                continue;
            }

            dto.setMatchedSkills(matched.subList(0, Math.min(6, matched.size())));
            int score = computeMatchScore(resumeKeywordSet, internshipKeywords, requiredKeywords);
            if (score <= 0) {
                continue;
            }
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

    private List<String> collectKeywords(String resumeText) {
        Set<String> keywords = new LinkedHashSet<>();

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
            if (token.chars().allMatch(Character::isDigit)) {
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
        return NOISE_TOKENS.contains(token);
    }

    private boolean looksLikeResume(String resumeText) {
        String normalized = safe(resumeText).toLowerCase(Locale.ENGLISH);
        
        // Minimum length check - resumes should be substantial
        if (normalized.length() < 500) {
            return false;
        }

        // Count resume section keywords
        int sectionHits = 0;
        for (String section : RESUME_SECTION_HINTS) {
            if (normalized.contains(section)) {
                sectionHits++;
            }
        }

        // Must have at least 3 resume sections (like education, experience, skills)
        if (sectionHits < 3) {
            return false;
        }

        // Check for contact information
        boolean hasEmail = EMAIL_PATTERN.matcher(normalized).find();
        boolean hasPhone = PHONE_PATTERN.matcher(normalized).find();
        
        // Resume should have email (mandatory for job applications)
        if (!hasEmail) {
            return false;
        }

        // Check word count - resumes should have meaningful content
        int tokenCount = normalized.split("\\s+").length;
        if (tokenCount < 100) {
            return false;
        }

        // Check for at least one technical keyword to confirm it's a professional resume
        int techHits = 0;
        for (String tech : TECH_KEYWORD_HINTS) {
            if (normalized.contains(tech)) {
                techHits++;
                if (techHits >= 1) {
                    break;
                }
            }
        }

        // Resume must have contact info, proper structure, and some technical content
        return sectionHits >= 3 && hasEmail && tokenCount >= 100 && techHits >= 1;
    }

    private boolean hasEnoughTechnicalSignals(List<String> extractedKeywords) {
        if (extractedKeywords == null || extractedKeywords.isEmpty()) {
            return false;
        }

        Set<String> knownSkillTokens = new HashSet<>(TECH_KEYWORD_HINTS);
        for (Internship internship : internshipRepository.findAll()) {
            addTokens(knownSkillTokens, safe(internship.getRequiredSkills()));
        }

        int technicalHits = 0;
        for (String keyword : extractedKeywords) {
            String normalized = safe(keyword).toLowerCase(Locale.ENGLISH);
            if (knownSkillTokens.contains(normalized)) {
                technicalHits++;
            }
            if (technicalHits >= 2) {
                return true;
            }
        }

        return false;
    }

    private String extractTextSafely(String fileName, String contentType, byte[] fileBytes) {
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ENGLISH);
        String lowerFileName = safe(fileName).toLowerCase(Locale.ENGLISH);

        if (lowerType.equals("application/pdf") || lowerFileName.endsWith(".pdf")) {
            try (PDDocument document = PDDocument.load(fileBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = safe(stripper.getText(document));
                return trimToLimit(text, 12000);
            } catch (Exception ignored) {
                // Fall back to plain decoding below when PDF text extraction fails.
            }
        }

        if (lowerType.contains("text") || lowerFileName.endsWith(".txt")) {
            return trimToLimit(new String(fileBytes, StandardCharsets.UTF_8), 12000);
        }

        String decoded = new String(fileBytes, StandardCharsets.UTF_8)
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return trimToLimit(decoded, 6000);
    }

    private String trimToLimit(String text, int limit) {
        String normalized = safe(text).replaceAll("\\s+", " ").trim();
        if (normalized.length() > limit) {
            return normalized.substring(0, limit);
        }
        return normalized;
    }

    private List<JobRecommendationDto> buildLocalRecommendations(List<String> keywords) {
        List<JobRecommendationDto> localRecommendations = new ArrayList<>();
        List<Internship> internships = internshipRepository.findAll();
        Set<String> resumeKeywordSet = toKeywordSet(keywords);

        for (Internship internship : internships) {
            Set<String> internshipKeywords = extractInternshipKeywords(internship);
            Set<String> requiredKeywords = extractTokensFromText(safe(internship.getRequiredSkills()));
            List<String> matched = orderedMatchedKeywords(keywords, internshipKeywords);

            if (matched.size() < 2 || internshipKeywords.isEmpty()) {
                continue;
            }

            JobRecommendationDto dto = new JobRecommendationDto();
            dto.setTitle(safe(internship.getTitle()));
            dto.setCompany(safe(internship.getCompany()));
            dto.setLocation(safe(internship.getLocation()));
            dto.setType(safe(internship.getInternshipType()));
            dto.setSource("Local Database");
            String url = !safe(internship.getApplyLink()).isBlank()
                    ? internship.getApplyLink()
                    : internship.getApplicationLink();
            dto.setUrl(safe(url));
            dto.setMatchedSkills(matched.subList(0, Math.min(6, matched.size())));
            dto.setMatchScore(computeMatchScore(resumeKeywordSet, internshipKeywords, requiredKeywords));
            if (dto.getMatchScore() <= 0) {
                continue;
            }
            localRecommendations.add(dto);
        }

        localRecommendations.sort(Comparator.comparingInt(JobRecommendationDto::getMatchScore).reversed());
        if (localRecommendations.size() > 20) {
            return new ArrayList<>(localRecommendations.subList(0, 20));
        }
        return localRecommendations;
    }

    private String summarizeException(Exception exception) {
        if (exception == null) {
            return "unknown error";
        }
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        if (exception.getCause() != null && exception.getCause().getMessage() != null
                && !exception.getCause().getMessage().isBlank()) {
            return exception.getCause().getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private Set<String> toKeywordSet(List<String> keywords) {
        Set<String> values = new HashSet<>();
        if (keywords == null) {
            return values;
        }
        for (String keyword : keywords) {
            String normalized = safe(keyword).toLowerCase(Locale.ENGLISH);
            if (!normalized.isBlank() && !isNoise(normalized)) {
                values.add(normalized);
            }
        }
        return values;
    }

    private Set<String> extractTokensFromText(String text) {
        Set<String> tokens = new HashSet<>();
        addTokens(tokens, text);
        return tokens;
    }

    private Set<String> extractInternshipKeywords(JsonNode item) {
        String combined = String.join(" ",
                readText(item, "title", "jobTitle", "role", "position"),
                readText(item, "company", "companyName", "organization"),
                readText(item, "location", "city", "place"),
                readText(item, "type", "employmentType", "internshipType"),
                readText(item, "description", "summary", "requirements", "skills", "requiredSkills")
        );
        return extractTokensFromText(combined);
    }

    private Set<String> extractInternshipKeywords(Internship internship) {
        String combined = String.join(" ",
                safe(internship.getTitle()),
                safe(internship.getCompany()),
                safe(internship.getLocation()),
                safe(internship.getInternshipType()),
                safe(internship.getDescription()),
                safe(internship.getRequirements()),
                safe(internship.getRequiredSkills())
        );
        return extractTokensFromText(combined);
    }

    private List<String> orderedMatchedKeywords(List<String> orderedResumeKeywords, Set<String> internshipKeywords) {
        List<String> matched = new ArrayList<>();
        if (orderedResumeKeywords == null || internshipKeywords == null || internshipKeywords.isEmpty()) {
            return matched;
        }

        for (String keyword : orderedResumeKeywords) {
            String normalized = safe(keyword).toLowerCase(Locale.ENGLISH);
            if (!normalized.isBlank()
                    && isSkillSignal(normalized)
                    && internshipKeywords.contains(normalized)
                    && !matched.contains(normalized)) {
                matched.add(normalized);
            }
        }
        return matched;
    }

    private int computeMatchScore(Set<String> resumeKeywords,
                                  Set<String> internshipKeywords,
                                  Set<String> requiredKeywords) {
        Set<String> resumeSkillKeywords = filterSkillTokens(resumeKeywords);
        Set<String> internshipSkillKeywords = filterSkillTokens(internshipKeywords);
        Set<String> requiredSkillKeywords = filterSkillTokens(requiredKeywords);

        if (resumeSkillKeywords.isEmpty() || internshipSkillKeywords.isEmpty()) {
            return 0;
        }

        // Count matching keywords
        int overlapCount = 0;
        for (String token : resumeSkillKeywords) {
            if (internshipSkillKeywords.contains(token)) {
                overlapCount++;
            }
        }

        // Must have at least 2 matching keywords to be considered
        if (overlapCount < 2) {
            return 0;
        }

        // Calculate score based on required skills if available
        if (!requiredSkillKeywords.isEmpty()) {
            int requiredMatches = 0;
            for (String token : resumeSkillKeywords) {
                if (requiredSkillKeywords.contains(token)) {
                    requiredMatches++;
                }
            }

            // Must match at least 1 required skill
            if (requiredMatches == 0) {
                return 0;
            }

            // Calculate percentage based on required skills matched
            // This gives a realistic score (e.g., matched 3 out of 5 required = 60%)
            int requiredPercentage = (int) Math.round((requiredMatches * 100.0) / requiredSkillKeywords.size());
            
            // Bonus for matching many skills beyond requirements
            int bonusForExtraSkills = Math.min(20, overlapCount - requiredMatches);
            
            int totalScore = Math.min(100, requiredPercentage + bonusForExtraSkills);
            return totalScore >= 15 ? totalScore : 0; // Minimum 15% to show
        }

        // If no required skills specified, calculate based on keyword density
        // Use smaller set (resume or job keywords) as denominator for fairer percentage
        int baseSize = Math.min(resumeSkillKeywords.size(), Math.min(internshipSkillKeywords.size(), 20));
        int matchPercentage = (int) Math.round((overlapCount * 100.0) / baseSize);
        
        // Cap between 15 and 95 for realistic scores
        int finalScore = Math.min(95, Math.max(0, matchPercentage));
        
        // Only return if score is meaningful (at least 15%)
        return finalScore >= 15 ? finalScore : 0;
    }

    private Set<String> filterSkillTokens(Set<String> tokens) {
        Set<String> filtered = new HashSet<>();
        if (tokens == null || tokens.isEmpty()) {
            return filtered;
        }
        for (String token : tokens) {
            String normalized = safe(token).toLowerCase(Locale.ENGLISH);
            if (!normalized.isBlank() && isSkillSignal(normalized)) {
                filtered.add(normalized);
            }
        }
        return filtered;
    }

    private boolean isSkillSignal(String token) {
        if (token == null || token.isBlank() || isNoise(token)) {
            return false;
        }
        if (TECH_KEYWORD_HINTS.contains(token)) {
            return true;
        }
        if (token.length() < 2 || token.length() > 24) {
            return false;
        }

        // Keep common engineering terms while filtering generic prose words.
        return token.matches("[a-z0-9+#.\\-]{2,24}")
                && (token.contains("sql")
                || token.contains("api")
                || token.contains("cloud")
                || token.contains("engineer")
                || token.contains("developer")
                || token.contains("backend")
                || token.contains("frontend")
                || token.contains("devops")
                || token.contains("data")
                || token.contains("ml")
                || token.contains("ai")
                || token.contains("react")
                || token.contains("spring")
                || token.contains("node")
                || token.contains("python")
                || token.contains("java"));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isSupportedResumeFile(MultipartFile file) {
        String fileName = safe(file.getOriginalFilename()).toLowerCase(Locale.ENGLISH);
        String contentType = safe(file.getContentType()).toLowerCase(Locale.ENGLISH);

        boolean extensionAllowed = fileName.endsWith(".pdf")
                || fileName.endsWith(".doc")
                || fileName.endsWith(".docx")
                || fileName.endsWith(".txt");

        boolean contentTypeAllowed = contentType.equals("application/pdf")
                || contentType.equals("application/msword")
                || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || contentType.equals("text/plain")
                || contentType.equals("application/octet-stream");

        return extensionAllowed || contentTypeAllowed;
    }
}

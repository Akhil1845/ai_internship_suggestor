package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.entity.Internship;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.dto.MatchedInternshipDTO;
import com.example.internship_ai_backend.repository.InternshipRepository;
import com.example.internship_ai_backend.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InternshipMatchingService {

    private static final Set<String> SKILL_NOISE = Set.of(
            "experience", "experienced", "project", "projects", "skill", "skills", "resume", "candidate",
            "platform", "multiple", "seeking", "motivated", "technical", "development", "learning", "sharing", "document", "client",
            "team", "role", "position", "work", "working", "the", "and", "for", "with", "from", "into", "about"
    );

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private StudentRepository studentRepository;

    /**
     * Match internships based on student's resume (skills and preferred domain)
     */
    public List<MatchedInternshipDTO> matchInternships(String email) {
        // Get student profile
        Optional<Student> studentOpt = studentRepository.findByEmail(email);
        if (!studentOpt.isPresent()) {
            return new ArrayList<>();
        }
        
        Student student = studentOpt.get();

        // Get all internships
        List<Internship> allInternships = internshipRepository.findAll();
        if (allInternships == null || allInternships.isEmpty()) {
            return new ArrayList<>();
        }

        // Parse student skills
        Set<String> studentSkills = parseSkills(student.getSkills());
        String preferredDomain = student.getPreferredDomain() != null ? 
                                student.getPreferredDomain().toLowerCase() : "";

        // Match and calculate relevance score
        List<MatchedInternshipDTO> matchedInternships = allInternships.stream()
            .map(internship -> {
                int matchScore = calculateMatchScore(internship, studentSkills, preferredDomain);
                if (matchScore > 0) {
                    String matchedSkills = extractMatchedSkills(internship, studentSkills);
                    // Prefer applyLink over applicationLink
                    String link = internship.getApplyLink() != null && !internship.getApplyLink().isEmpty() 
                        ? internship.getApplyLink() 
                        : internship.getApplicationLink();
                    
                    return new MatchedInternshipDTO(
                        internship.getId(),
                        internship.getTitle(),
                        internship.getCompany(),
                        internship.getDescription(),
                        internship.getLocation(),
                        internship.getDuration(),
                        internship.getStipend(),
                        link,
                        matchScore,
                        matchedSkills
                    );
                }
                return null;
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(MatchedInternshipDTO::getMatchScore).reversed())
            .collect(Collectors.toList());

        return matchedInternships;
    }

    /**
     * Calculate match score between student skills and internship requirements
     */
    private int calculateMatchScore(Internship internship, Set<String> studentSkills, String preferredDomain) {
        int score = 0;
        int matchedSkillsCount = 0;

        // Parse internship required skills
        Set<String> requiredSkills = parseSkills(internship.getRequiredSkills());
        
        // Score based on exact skill matches
        for (String skill : studentSkills) {
            if (requiredSkills.contains(skill.toLowerCase())) {
                matchedSkillsCount++;
                score += 10; // 10 points per matching skill
            }
        }

        // If required skills exist, must match at least 1 to proceed
        if (!requiredSkills.isEmpty() && matchedSkillsCount == 0) {
            return 0; // No match if no required skills met
        }

        // If no skills field, match against title and description keywords
        if (requiredSkills.isEmpty()) {
            String title = internship.getTitle() != null ? internship.getTitle().toLowerCase() : "";
            String description = internship.getDescription() != null ? internship.getDescription().toLowerCase() : "";
            String combined = title + " " + description;
            
            for (String skill : studentSkills) {
                if (combined.contains(skill.toLowerCase())) {
                    matchedSkillsCount++;
                    score += 8; // 8 points per keyword match
                }
            }
        }

        // Must have at least 2 skill matches to be considered
        if (matchedSkillsCount < 2) {
            return 0;
        }

        // Domain preference bonus
        String title = internship.getTitle().toLowerCase();
        String description = internship.getDescription() != null ? 
                            internship.getDescription().toLowerCase() : "";
        
        if (!preferredDomain.isEmpty()) {
            if (title.contains(preferredDomain) || description.contains(preferredDomain)) {
                score += 15; // Bonus for domain match
            }
        }

        // Calculate percentage based on required skills
        int percentage;
        if (!requiredSkills.isEmpty()) {
            // Calculate as percentage of required skills matched
            percentage = (matchedSkillsCount * 100) / requiredSkills.size();
            // Add base score points
            percentage = Math.min(100, percentage + (score / 10));
        } else {
            // Convert points to percentage (max ~80 for non-required matches)
            percentage = Math.min(80, score);
        }

        // Minimum threshold of 15% to show
        return percentage >= 15 ? percentage : 0;
    }

    /**
     * Extract matched skills between student and internship
     */
    private String extractMatchedSkills(Internship internship, Set<String> studentSkills) {
        Set<String> requiredSkills = parseSkills(internship.getRequiredSkills());
        List<String> matched = new ArrayList<>();

        // First try exact skill matches
        for (String skill : studentSkills) {
            if (requiredSkills.contains(skill.toLowerCase())) {
                matched.add(skill);
            }
        }

        // If no exact matches, look for keywords in title/description
        if (matched.isEmpty()) {
            String title = internship.getTitle() != null ? internship.getTitle().toLowerCase() : "";
            String description = internship.getDescription() != null ? internship.getDescription().toLowerCase() : "";
            String combined = title + " " + description;
            
            for (String skill : studentSkills) {
                if (combined.contains(skill.toLowerCase())) {
                    matched.add(skill);
                    if (matched.size() >= 5) break; // Limit to top 5
                }
            }
        }

        return matched.isEmpty() ? "Tech role" : String.join(", ", matched);
    }

    /**
     * Parse comma-separated skills into a set of lowercase skill names
     */
    private Set<String> parseSkills(String skillsStr) {
        if (skillsStr == null || skillsStr.trim().isEmpty()) {
            return new HashSet<>();
        }

        Set<String> parsed = new HashSet<>();
        String normalized = skillsStr.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9+#.\\-/,;\\n ]", " ");

        for (String part : normalized.split("[,;/|\\n]+")) {
            String phrase = part.trim();
            if (phrase.isEmpty()) {
                continue;
            }

            if (isLikelySkillToken(phrase)) {
                parsed.add(phrase);
            }

            for (String token : phrase.split("\\s+")) {
                String cleaned = token.trim();
                if (isLikelySkillToken(cleaned)) {
                    parsed.add(cleaned);
                }
            }
        }

        return parsed;
    }

    private boolean isLikelySkillToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalized = token.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.length() < 2 || normalized.length() > 28) {
            return false;
        }
        if (SKILL_NOISE.contains(normalized)) {
            return false;
        }
        return normalized.matches("[a-z0-9+#.\\- ]+");
    }

    /**
     * Get all available internships without matching
     */
    public List<MatchedInternshipDTO> getAllInternships() {
        List<Internship> allInternships = internshipRepository.findAll();
        
        if (allInternships == null || allInternships.isEmpty()) {
            return new ArrayList<>();
        }

        return allInternships.stream()
            .map(internship -> {
                // Prefer applyLink over applicationLink
                String link = internship.getApplyLink() != null && !internship.getApplyLink().isEmpty() 
                    ? internship.getApplyLink() 
                    : internship.getApplicationLink();
                
                return new MatchedInternshipDTO(
                    internship.getId(),
                    internship.getTitle(),
                    internship.getCompany(),
                    internship.getDescription(),
                    internship.getLocation(),
                    internship.getDuration(),
                    internship.getStipend(),
                    link,
                    0, // No match score for all internships
                    "Available position"
                );
            })
            .collect(Collectors.toList());
    }
}

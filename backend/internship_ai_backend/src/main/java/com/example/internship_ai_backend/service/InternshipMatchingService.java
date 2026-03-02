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

        // Parse internship required skills
        Set<String> requiredSkills = parseSkills(internship.getRequiredSkills());
        
        // Score based on exact skill matches
        int skillMatches = 0;
        for (String skill : studentSkills) {
            if (requiredSkills.contains(skill.toLowerCase())) {
                skillMatches++;
            }
        }

        if (skillMatches > 0) {
            score += skillMatches * 15; // 15 points per matching skill
        }

        // If no skills field, match against title and description keywords
        if (requiredSkills.isEmpty()) {
            String title = internship.getTitle() != null ? internship.getTitle().toLowerCase() : "";
            String description = internship.getDescription() != null ? internship.getDescription().toLowerCase() : "";
            String combined = title + " " + description;
            
            int keywordMatches = 0;
            for (String skill : studentSkills) {
                if (combined.contains(skill.toLowerCase())) {
                    keywordMatches++;
                }
            }
            
            if (keywordMatches > 0) {
                score += keywordMatches * 10; // 10 points per keyword match
            }
        }

        // Give minimum score if no matches yet but title contains tech keywords
        if (score == 0) {
            String title = internship.getTitle() != null ? internship.getTitle().toLowerCase() : "";
            if (title.contains("developer") || title.contains("engineer") || 
                title.contains("software") || title.contains("tech") || 
                title.contains("full stack") || title.contains("backend") || 
                title.contains("frontend") || title.contains("ai") || 
                title.contains("data") || title.contains("devops")) {
                score = 5; // Minimum score for tech-related jobs
            }
        }

        // Domain preference bonus
        String title = internship.getTitle().toLowerCase();
        String description = internship.getDescription() != null ? 
                            internship.getDescription().toLowerCase() : "";
        
        if (!preferredDomain.isEmpty()) {
            if (title.contains(preferredDomain) || description.contains(preferredDomain)) {
                score += 20;
            }
        }

        // Bonus for full-time/long-term internships
        String duration = internship.getDuration() != null ? 
                         internship.getDuration().toLowerCase() : "";
        if (duration.contains("6") || duration.contains("full") || duration.contains("semester")) {
            score += 10;
        }

        // Bonus for internships with stipend
        String stipend = internship.getStipend() != null ? internship.getStipend() : "";
        if (!stipend.isEmpty() && !stipend.toLowerCase().contains("unpaid")) {
            score += 5;
        }

        return score;
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

        return Arrays.stream(skillsStr.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
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

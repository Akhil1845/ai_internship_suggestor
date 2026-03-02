package com.example.internship_ai_backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ResumeRecommendationsResponse {

    private String message;
    private List<String> extractedKeywords = new ArrayList<>();
    private List<JobRecommendationDto> recommendations = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getExtractedKeywords() {
        return extractedKeywords;
    }

    public void setExtractedKeywords(List<String> extractedKeywords) {
        this.extractedKeywords = extractedKeywords;
    }

    public List<JobRecommendationDto> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<JobRecommendationDto> recommendations) {
        this.recommendations = recommendations;
    }
}

package com.example.internship_ai_backend.dto;

public class MatchedInternshipDTO {
    private Integer id;
    private String title;
    private String company;
    private String description;
    private String location;
    private String duration;
    private String stipend;
    private String applicationLink;
    private Integer matchScore;
    private String matchedSkills;

    public MatchedInternshipDTO() {}

    public MatchedInternshipDTO(Integer id, String title, String company, String description,
                               String location, String duration, String stipend, 
                               String applicationLink, Integer matchScore, String matchedSkills) {
        this.id = id;
        this.title = title;
        this.company = company;
        this.description = description;
        this.location = location;
        this.duration = duration;
        this.stipend = stipend;
        this.applicationLink = applicationLink;
        this.matchScore = matchScore;
        this.matchedSkills = matchedSkills;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getStipend() { return stipend; }
    public void setStipend(String stipend) { this.stipend = stipend; }

    public String getApplicationLink() { return applicationLink; }
    public void setApplicationLink(String applicationLink) { this.applicationLink = applicationLink; }

    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }

    public String getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(String matchedSkills) { this.matchedSkills = matchedSkills; }
}

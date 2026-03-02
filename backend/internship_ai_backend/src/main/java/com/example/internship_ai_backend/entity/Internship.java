package com.example.internship_ai_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "internships")
public class Internship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "required_skills")
    private String requiredSkills;

    private String location;

    private String duration;

    private String stipend;

    @Column(name = "application_link")
    private String applicationLink;

    @Column(name = "apply_link")
    private String applyLink;

    @Column(name = "posted_date")
    private LocalDateTime postedDate;

    @Column(name = "deadline_date")
    private LocalDateTime deadlineDate;

    @Column(name = "internship_type")
    private String internshipType;

    private Integer relevanceScore;

    public Internship() {}

    public Internship(String title, String company, String description, String requirements, 
                     String requiredSkills, String location, String duration, String stipend, 
                     String applicationLink, LocalDateTime postedDate, String internshipType) {
        this.title = title;
        this.company = company;
        this.description = description;
        this.requirements = requirements;
        this.requiredSkills = requiredSkills;
        this.location = location;
        this.duration = duration;
        this.stipend = stipend;
        this.applicationLink = applicationLink;
        this.postedDate = postedDate;
        this.internshipType = internshipType;
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

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getStipend() { return stipend; }
    public void setStipend(String stipend) { this.stipend = stipend; }

    public String getApplicationLink() { return applicationLink; }
    public void setApplicationLink(String applicationLink) { this.applicationLink = applicationLink; }

    public String getApplyLink() { return applyLink; }
    public void setApplyLink(String applyLink) { this.applyLink = applyLink; }

    public LocalDateTime getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDateTime postedDate) { this.postedDate = postedDate; }

    public LocalDateTime getDeadlineDate() { return deadlineDate; }
    public void setDeadlineDate(LocalDateTime deadlineDate) { this.deadlineDate = deadlineDate; }

    public String getInternshipType() { return internshipType; }
    public void setInternshipType(String internshipType) { this.internshipType = internshipType; }

    public Integer getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(Integer relevanceScore) { this.relevanceScore = relevanceScore; }
}

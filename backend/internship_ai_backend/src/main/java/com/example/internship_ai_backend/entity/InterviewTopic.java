package com.example.internship_ai_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_topics")
public class InterviewTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 600)
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(length = 1200)
    private String keywords;

    @Column(name = "source_url", length = 1200, unique = true)
    private String sourceUrl;

    @Column(name = "scraped_at")
    private LocalDateTime scrapedAt;

    @Column(name = "times_viewed", nullable = false)
    private Integer timesViewed = 0;

    /* ── Enums ─────────────────────────────── */

    public enum Category {
        JAVA, DSA, DBMS, OS, CN, APTITUDE, HR
    }

    public enum Company {
        AMAZON, GOOGLE, MICROSOFT, FLIPKART, INFOSYS, TCS,
        WIPRO, ACCENTURE, COGNIZANT, CAPGEMINI, GENERAL
    }

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    /* ── Lifecycle ──────────────────────────── */

    @PrePersist
    protected void onCreate() {
        if (this.scrapedAt == null) {
            this.scrapedAt = LocalDateTime.now();
        }
        if (this.timesViewed == null) {
            this.timesViewed = 0;
        }
    }

    /* ── Getters & Setters ─────────────────── */

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public LocalDateTime getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(LocalDateTime scrapedAt) { this.scrapedAt = scrapedAt; }

    public Integer getTimesViewed() { return timesViewed; }
    public void setTimesViewed(Integer timesViewed) { this.timesViewed = timesViewed; }
}

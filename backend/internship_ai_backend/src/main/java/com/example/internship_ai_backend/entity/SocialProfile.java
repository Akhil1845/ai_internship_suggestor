package com.example.internship_ai_backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_social_profiles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "platform"}))
public class SocialProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(name = "profile_url", nullable = false, length = 1000)
    private String profileUrl;

    @Column(nullable = false)
    private String username;

    // ================= CODING STATS =================
    private Integer problemsSolved = 0;
    private Integer contestRating = 0;
    private Integer globalRank = 0;
    private Integer daysActive = 0;

    // ================= GITHUB STATS =================
    private Integer repositoriesCount = 0;
    private Integer totalCommits = 0;
    private Integer followers = 0;

    // ================= LINKEDIN STATS =================
    private Integer connections = 0;
    private Integer posts = 0;

    // ================= RELATIONSHIP =================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    // ================= TIMESTAMPS =================
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ================= GETTERS & SETTERS =================

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public String getProfileUrl() { return profileUrl; }
    public void setProfileUrl(String profileUrl) { this.profileUrl = profileUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Integer getProblemsSolved() { return problemsSolved; }
    public void setProblemsSolved(Integer problemsSolved) { this.problemsSolved = problemsSolved; }

    public Integer getContestRating() { return contestRating; }
    public void setContestRating(Integer contestRating) { this.contestRating = contestRating; }

    public Integer getGlobalRank() { return globalRank; }
    public void setGlobalRank(Integer globalRank) { this.globalRank = globalRank; }

    public Integer getDaysActive() { return daysActive; }
    public void setDaysActive(Integer daysActive) { this.daysActive = daysActive; }

    public Integer getRepositoriesCount() { return repositoriesCount; }
    public void setRepositoriesCount(Integer repositoriesCount) { this.repositoriesCount = repositoriesCount; }

    public Integer getTotalCommits() { return totalCommits; }
    public void setTotalCommits(Integer totalCommits) { this.totalCommits = totalCommits; }

    public Integer getFollowers() { return followers; }
    public void setFollowers(Integer followers) { this.followers = followers; }

    public Integer getConnections() { return connections; }
    public void setConnections(Integer connections) { this.connections = connections; }

    public Integer getPosts() { return posts; }
    public void setPosts(Integer posts) { this.posts = posts; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
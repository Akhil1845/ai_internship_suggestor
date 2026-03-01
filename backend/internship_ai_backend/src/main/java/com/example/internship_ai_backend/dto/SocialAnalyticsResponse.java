package com.example.internship_ai_backend.dto;

import java.util.ArrayList;
import java.util.List;

public class SocialAnalyticsResponse {

    private String platform;
    private String source;
    private String message;
    private String profileUrl;

    private List<String> barLabels = new ArrayList<>();
    private List<Integer> barData = new ArrayList<>();
    private List<String> lineLabels = new ArrayList<>();
    private List<Integer> lineData = new ArrayList<>();

    private Integer problemsAttempted = 0;
    private Integer contestStats = 0;
    private Integer activityStreak = 0;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public List<String> getBarLabels() {
        return barLabels;
    }

    public void setBarLabels(List<String> barLabels) {
        this.barLabels = barLabels;
    }

    public List<Integer> getBarData() {
        return barData;
    }

    public void setBarData(List<Integer> barData) {
        this.barData = barData;
    }

    public List<String> getLineLabels() {
        return lineLabels;
    }

    public void setLineLabels(List<String> lineLabels) {
        this.lineLabels = lineLabels;
    }

    public List<Integer> getLineData() {
        return lineData;
    }

    public void setLineData(List<Integer> lineData) {
        this.lineData = lineData;
    }

    public Integer getProblemsAttempted() {
        return problemsAttempted;
    }

    public void setProblemsAttempted(Integer problemsAttempted) {
        this.problemsAttempted = problemsAttempted;
    }

    public Integer getContestStats() {
        return contestStats;
    }

    public void setContestStats(Integer contestStats) {
        this.contestStats = contestStats;
    }

    public Integer getActivityStreak() {
        return activityStreak;
    }

    public void setActivityStreak(Integer activityStreak) {
        this.activityStreak = activityStreak;
    }
}

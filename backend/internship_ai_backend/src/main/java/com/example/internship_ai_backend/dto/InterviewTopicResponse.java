package com.example.internship_ai_backend.dto;

import com.example.internship_ai_backend.entity.InterviewTopic;
import java.time.LocalDateTime;

public class InterviewTopicResponse {

    private Integer id;
    private String title;
    private String content;
    private String contentPreview;
    private String category;
    private String company;
    private String difficulty;
    private String keywords;
    private String sourceUrl;
    private LocalDateTime scrapedAt;
    private Integer timesViewed;

    public static InterviewTopicResponse from(InterviewTopic t) {
        InterviewTopicResponse r = new InterviewTopicResponse();
        r.id           = t.getId();
        r.title        = t.getTitle();
        r.content      = t.getContent();
        r.contentPreview = buildPreview(t.getContent());
        r.category     = t.getCategory() != null ? t.getCategory().name() : null;
        r.company      = t.getCompany()  != null ? t.getCompany().name()  : null;
        r.difficulty   = t.getDifficulty() != null ? t.getDifficulty().name() : null;
        r.keywords     = t.getKeywords();
        r.sourceUrl    = t.getSourceUrl();
        r.scrapedAt    = t.getScrapedAt();
        r.timesViewed  = t.getTimesViewed();
        return r;
    }

    private static String buildPreview(String content) {
        if (content == null || content.isBlank()) return "";
        String stripped = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return stripped.length() > 300 ? stripped.substring(0, 300) + "…" : stripped;
    }

    /* ── Getters ────────────────────────────── */
    public Integer getId()            { return id; }
    public String getTitle()          { return title; }
    public String getContent()        { return content; }
    public String getContentPreview() { return contentPreview; }
    public String getCategory()       { return category; }
    public String getCompany()        { return company; }
    public String getDifficulty()     { return difficulty; }
    public String getKeywords()       { return keywords; }
    public String getSourceUrl()      { return sourceUrl; }
    public LocalDateTime getScrapedAt() { return scrapedAt; }
    public Integer getTimesViewed()   { return timesViewed; }
}

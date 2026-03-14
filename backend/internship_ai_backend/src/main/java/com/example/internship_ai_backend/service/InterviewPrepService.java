package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.dto.InterviewTopicResponse;
import com.example.internship_ai_backend.entity.InterviewTopic;
import com.example.internship_ai_backend.entity.InterviewTopic.Category;
import com.example.internship_ai_backend.entity.InterviewTopic.Company;
import com.example.internship_ai_backend.repository.InterviewTopicRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class InterviewPrepService {

    private final InterviewTopicRepository repo;
    private final GfgScraperService scraper;

    public InterviewPrepService(InterviewTopicRepository repo, GfgScraperService scraper) {
        this.repo    = repo;
        this.scraper = scraper;
    }

    /* ── List / Filter ─────────────────────────────── */

    public List<InterviewTopicResponse> getAll() {
        return repo.findAllByOrderByScrapedAtDesc()
                   .stream().map(InterviewTopicResponse::from).collect(Collectors.toList());
    }

    public List<InterviewTopicResponse> getByCategory(String categoryStr) {
        Category cat = parseCategory(categoryStr);
        if (cat == null) return getAll();
        return repo.findByCategoryOrderByTimesViewedDesc(cat)
                   .stream().map(InterviewTopicResponse::from).collect(Collectors.toList());
    }

    public List<InterviewTopicResponse> getByCompany(String companyStr) {
        Company com = parseCompany(companyStr);
        if (com == null) return getAll();
        return repo.findByCompanyOrderByTimesViewedDesc(com)
                   .stream().map(InterviewTopicResponse::from).collect(Collectors.toList());
    }

    public List<InterviewTopicResponse> getByCategoryAndCompany(String categoryStr, String companyStr) {
        Category cat = parseCategory(categoryStr);
        Company  com = parseCompany(companyStr);
        if (cat == null && com == null) return getAll();
        if (cat == null) return getByCompany(companyStr);
        if (com == null) return getByCategory(categoryStr);
        return repo.findByCategoryAndCompanyOrderByTimesViewedDesc(cat, com)
                   .stream().map(InterviewTopicResponse::from).collect(Collectors.toList());
    }

    /* ── Search ────────────────────────────────────── */

    public List<InterviewTopicResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAll();
        return repo.searchByKeyword(keyword.trim())
                   .stream().map(InterviewTopicResponse::from).collect(Collectors.toList());
    }

    /* ── Single topic with view-count increment ────── */

    @Transactional
    public InterviewTopicResponse getById(Integer id) {
        InterviewTopic topic = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + id));
        topic.setTimesViewed(topic.getTimesViewed() + 1);
        repo.save(topic);
        return InterviewTopicResponse.from(topic);
    }

    /* ── Trending ──────────────────────────────────── */

    public List<InterviewTopicResponse> getTrending(int limit) {
        return repo.findTopTrending(PageRequest.of(0, limit))
                   .stream().map(InterviewTopicResponse::from).collect(Collectors.toList());
    }

    /* ── Matched topics for a specific internship ──── */

    public List<InterviewTopicResponse> getTopicsForInternship(String title, String stack) {
        // Tokenise internship title + tech stack into individual keywords
        List<String> tokens = Stream.concat(
                token(title),
                token(stack)
        ).distinct().filter(t -> t.length() > 2).collect(Collectors.toList());

        return tokens.stream()
                .flatMap(kw -> repo.findByKeywordMatch(kw).stream())
                .distinct()
                .sorted((a, b) -> Integer.compare(b.getTimesViewed(), a.getTimesViewed()))
                .limit(12)
                .map(InterviewTopicResponse::from)
                .collect(Collectors.toList());
    }

    /* ── Admin scrape trigger ──────────────────────── */

    public int triggerScrape() {
        return scraper.triggerScrape();
    }

    /* ── Helpers ───────────────────────────────────── */

    private Stream<String> token(String text) {
        if (text == null || text.isBlank()) return Stream.empty();
        return Arrays.stream(text.toLowerCase().split("[,\\s/|+]+"))
                     .map(String::trim)
                     .filter(s -> !s.isBlank());
    }

    private Category parseCategory(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Category.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Company parseCompany(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Company.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}

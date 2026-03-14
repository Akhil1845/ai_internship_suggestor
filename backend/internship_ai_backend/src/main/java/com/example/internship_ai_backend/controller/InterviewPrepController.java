package com.example.internship_ai_backend.controller;

import com.example.internship_ai_backend.dto.ApiResponse;
import com.example.internship_ai_backend.dto.InterviewTopicResponse;
import com.example.internship_ai_backend.service.InterviewPrepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/prep/topics")
public class InterviewPrepController {

    private final InterviewPrepService service;

    public InterviewPrepController(InterviewPrepService service) {
        this.service = service;
    }

    /**
     * GET /api/prep/topics
     * Optional query params: category, company
     * Returns all topics, filtered by category and/or company when provided.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InterviewTopicResponse>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String company) {
        try {
            List<InterviewTopicResponse> data =
                    (category != null || company != null)
                    ? service.getByCategoryAndCompany(category, company)
                    : service.getAll();
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * GET /api/prep/topics/search?q=keyword
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<InterviewTopicResponse>>> search(
            @RequestParam(name = "q") String keyword) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.search(keyword)));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * GET /api/prep/topics/trending?limit=10
     */
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<InterviewTopicResponse>>> trending(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.getTrending(limit)));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * GET /api/prep/topics/for-internship?title=SDE&stack=Java,Spring
     */
    @GetMapping("/for-internship")
    public ResponseEntity<ApiResponse<List<InterviewTopicResponse>>> forInternship(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String stack) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    service.getTopicsForInternship(title, stack)));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * GET /api/prep/topics/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InterviewTopicResponse>> getById(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * POST /api/prep/scrape  — admin trigger to (re)scrape GFG
     */
    @PostMapping("/scrape")
    public ResponseEntity<ApiResponse<String>> scrape() {
        try {
            int count = service.triggerScrape();
            return ResponseEntity.ok(
                    ApiResponse.ok("Scrape complete", count + " new topics saved"));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}

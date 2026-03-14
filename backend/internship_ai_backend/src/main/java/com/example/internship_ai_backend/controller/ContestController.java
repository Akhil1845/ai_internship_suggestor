package com.example.internship_ai_backend.controller;

import com.example.internship_ai_backend.service.ContestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contests")
@CrossOrigin(origins = "*")
public class ContestController {

    private final ContestService contestService;

    public ContestController(ContestService contestService) {
        this.contestService = contestService;
    }

    /** GET /api/contests?platform=CODEFORCES  — upcoming contests for one platform */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getContests(
            @RequestParam(defaultValue = "CODEFORCES") String platform) {
        return ResponseEntity.ok(contestService.getContests(platform));
    }

    /** GET /api/contests/preferences?email=  — returns all three platform toggles */
    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Boolean>> getPreferences(@RequestParam String email) {
        return ResponseEntity.ok(contestService.getPreferences(email));
    }

    /** PUT /api/contests/preferences?email=&platform=&enabled=  — upsert one toggle */
    @PutMapping("/preferences")
    public ResponseEntity<Void> updatePreference(
            @RequestParam String email,
            @RequestParam String platform,
            @RequestParam boolean enabled) {
        contestService.updatePreference(email, platform, enabled);
        return ResponseEntity.ok().build();
    }
}

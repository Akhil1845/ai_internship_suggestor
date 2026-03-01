package com.example.internship_ai_backend.controller;

import com.example.internship_ai_backend.entity.Platform;
import com.example.internship_ai_backend.entity.SocialProfile;
import com.example.internship_ai_backend.service.SocialProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students/{studentId}/social-profiles")
@CrossOrigin
public class SocialProfileController {

    private final SocialProfileService socialProfileService;

    public SocialProfileController(SocialProfileService socialProfileService) {
        this.socialProfileService = socialProfileService;
    }

    // ✅ Add or Update profile
    @PostMapping
    public SocialProfile addOrUpdateProfile(
            @PathVariable Integer studentId,
            @RequestBody SocialProfile profile) {

        return socialProfileService.saveOrUpdateProfile(studentId, profile);
    }

    // ✅ Get all profiles
    @GetMapping
    public List<SocialProfile> getAllProfiles(@PathVariable Integer studentId) {
        return socialProfileService.getAllProfiles(studentId);
    }

    // ✅ Get specific platform profile
    @GetMapping("/{platform}")
    public SocialProfile getProfileByPlatform(
            @PathVariable Integer studentId,
            @PathVariable Platform platform) {

        return socialProfileService.getProfileByPlatform(studentId, platform);
    }

    // ✅ Delete profile
    @DeleteMapping("/{platform}")
    public String deleteProfile(
            @PathVariable Integer studentId,
            @PathVariable Platform platform) {

        socialProfileService.deleteProfile(studentId, platform);
        return "Profile deleted successfully";
    }
}
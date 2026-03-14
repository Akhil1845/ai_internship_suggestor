package com.example.internship_ai_backend.dto;

public class ProjectRequest {

    private String title;
    private String deployedLink;
    private String githubLink;
    private String techStack;
    private String description;
    private boolean featured;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDeployedLink() {
        return deployedLink;
    }

    public void setDeployedLink(String deployedLink) {
        this.deployedLink = deployedLink;
    }

    public String getGithubLink() {
        return githubLink;
    }

    public void setGithubLink(String githubLink) {
        this.githubLink = githubLink;
    }

    public String getTechStack() {
        return techStack;
    }

    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }
}

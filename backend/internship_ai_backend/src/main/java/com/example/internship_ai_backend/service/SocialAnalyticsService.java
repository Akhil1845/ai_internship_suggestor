package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.dto.SocialAnalyticsResponse;
import com.example.internship_ai_backend.entity.Platform;
import com.example.internship_ai_backend.entity.SocialProfile;
import com.example.internship_ai_backend.entity.Student;
import com.example.internship_ai_backend.repository.SocialProfileRepository;
import com.example.internship_ai_backend.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SocialAnalyticsService {

    private final StudentRepository studentRepository;
    private final SocialProfileRepository socialProfileRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SocialAnalyticsService(StudentRepository studentRepository,
                                  SocialProfileRepository socialProfileRepository) {
        this.studentRepository = studentRepository;
        this.socialProfileRepository = socialProfileRepository;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    public SocialAnalyticsResponse getAnalytics(String email, String platformKey) {
        SocialAnalyticsResponse response = new SocialAnalyticsResponse();

        if (email == null || email.trim().isEmpty()) {
            response.setSource("UNAVAILABLE");
            response.setMessage("Email is required");
            return response;
        }

        Platform platform = parsePlatform(platformKey);
        if (platform == null) {
            response.setSource("UNAVAILABLE");
            response.setMessage("Invalid platform");
            return response;
        }

        response.setPlatform(toDisplayPlatform(platform));

        Optional<Student> studentOpt = studentRepository.findByEmail(email.toLowerCase().trim());
        if (studentOpt.isEmpty()) {
            response.setSource("UNAVAILABLE");
            response.setMessage("User not found");
            return response;
        }

        Student student = studentOpt.get();
        Optional<SocialProfile> socialProfileOpt = socialProfileRepository.findByStudentIdAndPlatform(student.getId(), platform);

        if (socialProfileOpt.isEmpty()) {
            response.setSource("UNAVAILABLE");
            response.setMessage("Profile link not found. Add this platform link in Profile page first.");
            return response;
        }

        SocialProfile socialProfile = socialProfileOpt.get();
        response.setProfileUrl(socialProfile.getProfileUrl());

        try {
            switch (platform) {
                case GITHUB:
                    return loadGithubAnalytics(socialProfile, response);
                case LEETCODE:
                    return loadLeetCodeAnalytics(socialProfile, response);
                case HACKERRANK:
                case CODECHEF:
                case LINKEDIN:
                default:
                    return fromStoredAnalytics(socialProfile, response,
                            "Live analytics API is not publicly reliable for this platform yet; showing stored values.");
            }
        } catch (Exception ex) {
            return fromStoredAnalytics(socialProfile, response,
                    "Live fetch failed right now; showing last stored values.");
        }
    }

    private SocialAnalyticsResponse loadGithubAnalytics(SocialProfile socialProfile,
                                                        SocialAnalyticsResponse response) throws Exception {

        String username = resolveUsername(socialProfile);
        if (username.isEmpty()) {
            return fromStoredAnalytics(socialProfile, response, "Unable to resolve GitHub username from saved profile link.");
        }

        HttpRequest userRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/users/" + username))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ai-internship-platform")
                .GET()
                .build();

        HttpResponse<String> userResponse = httpClient.send(userRequest, HttpResponse.BodyHandlers.ofString());

        if (userResponse.statusCode() >= 400) {
            return fromStoredAnalytics(socialProfile, response, "GitHub profile not accessible. Check if username/link is valid.");
        }

        JsonNode userNode = objectMapper.readTree(userResponse.body());
        int repos = userNode.path("public_repos").asInt(0);
        int followers = userNode.path("followers").asInt(0);
        int following = userNode.path("following").asInt(0);

        HttpRequest eventsRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/users/" + username + "/events/public?per_page=100"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ai-internship-platform")
                .GET()
                .build();

        HttpResponse<String> eventsResponse = httpClient.send(eventsRequest, HttpResponse.BodyHandlers.ofString());

        List<JsonNode> events = new ArrayList<>();
        if (eventsResponse.statusCode() < 400) {
            JsonNode eventsNode = objectMapper.readTree(eventsResponse.body());
            if (eventsNode.isArray()) {
                for (JsonNode eventNode : eventsNode) {
                    events.add(eventNode);
                }
            }
        }

        int totalCommits = 0;
        Set<LocalDate> activeDays = new LinkedHashSet<>();

        YearMonth currentMonth = YearMonth.now();
        LinkedHashMap<YearMonth, Integer> monthlyActivity = new LinkedHashMap<>();
        for (int i = 4; i >= 0; i--) {
            monthlyActivity.put(currentMonth.minusMonths(i), 0);
        }

        for (JsonNode event : events) {
            String createdAt = event.path("created_at").asText("");
            if (!createdAt.isEmpty() && createdAt.length() >= 10) {
                LocalDate eventDate = LocalDate.parse(createdAt.substring(0, 10));
                activeDays.add(eventDate);

                YearMonth ym = YearMonth.from(eventDate);
                if (monthlyActivity.containsKey(ym)) {
                    monthlyActivity.put(ym, monthlyActivity.get(ym) + 1);
                }
            }

            if ("PushEvent".equals(event.path("type").asText(""))) {
                JsonNode commitsNode = event.path("payload").path("commits");
                if (commitsNode.isArray()) {
                    totalCommits += commitsNode.size();
                }
            }
        }

        int streak = computeStreak(activeDays);

        List<String> lineLabels = new ArrayList<>();
        List<Integer> lineData = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);
        for (Map.Entry<YearMonth, Integer> entry : monthlyActivity.entrySet()) {
            lineLabels.add(entry.getKey().format(formatter));
            lineData.add(entry.getValue());
        }

        response.setSource("LIVE");
        response.setMessage("Fetched from GitHub public API");
        response.setBarLabels(List.of("Repositories", "Followers", "Following"));
        response.setBarData(List.of(repos, followers, following));
        response.setLineLabels(lineLabels);
        response.setLineData(lineData);
        response.setProblemsAttempted(repos);
        response.setContestStats(totalCommits);
        response.setActivityStreak(streak);

        socialProfile.setRepositoriesCount(repos);
        socialProfile.setFollowers(followers);
        socialProfile.setTotalCommits(totalCommits);
        socialProfile.setDaysActive(streak);
        socialProfileRepository.save(socialProfile);

        return response;
    }

    private SocialAnalyticsResponse loadLeetCodeAnalytics(SocialProfile socialProfile,
                                                          SocialAnalyticsResponse response) throws Exception {

        String username = resolveUsername(socialProfile);
        if (username.isEmpty()) {
            return fromStoredAnalytics(socialProfile, response, "Unable to resolve LeetCode username from saved profile link.");
        }

        String query = "query userData($username: String!) { " +
                "matchedUser(username: $username) { submitStatsGlobal { acSubmissionNum { difficulty count } } } " +
                "userContestRanking(username: $username) { rating attendedContestsCount globalRanking } " +
                "userContestRankingHistory(username: $username) { attended rating contest { title } } " +
                "}";

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query);
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("username", username);
        payload.set("variables", variables);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://leetcode.com/graphql"))
                .header("Content-Type", "application/json")
                .header("Referer", "https://leetcode.com")
                .header("User-Agent", "ai-internship-platform")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> apiResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (apiResponse.statusCode() >= 400) {
            return fromStoredAnalytics(socialProfile, response, "LeetCode API is not reachable for this profile right now.");
        }

        JsonNode root = objectMapper.readTree(apiResponse.body());
        JsonNode dataNode = root.path("data");

        JsonNode matchedUserNode = dataNode.path("matchedUser");
        if (matchedUserNode.isMissingNode() || matchedUserNode.isNull()) {
            return fromStoredAnalytics(socialProfile, response, "LeetCode user not found or profile is not public.");
        }

        int easy = 0;
        int medium = 0;
        int hard = 0;

        JsonNode submissionArray = matchedUserNode.path("submitStatsGlobal").path("acSubmissionNum");
        if (submissionArray.isArray()) {
            for (JsonNode item : submissionArray) {
                String difficulty = item.path("difficulty").asText("");
                int count = item.path("count").asInt(0);
                if ("Easy".equalsIgnoreCase(difficulty)) easy = count;
                if ("Medium".equalsIgnoreCase(difficulty)) medium = count;
                if ("Hard".equalsIgnoreCase(difficulty)) hard = count;
            }
        }

        JsonNode rankingNode = dataNode.path("userContestRanking");
        int contests = rankingNode.path("attendedContestsCount").asInt(0);
        int contestRating = (int) Math.round(rankingNode.path("rating").asDouble(0));
        int globalRank = rankingNode.path("globalRanking").asInt(0);

        List<String> lineLabels = new ArrayList<>();
        List<Integer> lineData = new ArrayList<>();

        JsonNode historyArray = dataNode.path("userContestRankingHistory");
        if (historyArray.isArray()) {
            List<String> tempLabels = new ArrayList<>();
            List<Integer> tempData = new ArrayList<>();

            for (JsonNode item : historyArray) {
                boolean attended = item.path("attended").asBoolean(false);
                int rating = (int) Math.round(item.path("rating").asDouble(0));
                String title = item.path("contest").path("title").asText("Contest");

                if (attended && rating > 0) {
                    tempLabels.add(title);
                    tempData.add(rating);
                }
            }

            int start = Math.max(0, tempData.size() - 5);
            for (int i = start; i < tempData.size(); i++) {
                lineLabels.add("C" + (i - start + 1));
                lineData.add(tempData.get(i));
            }
        }

        if (lineData.isEmpty() && contestRating > 0) {
            lineLabels = List.of("Current");
            lineData = List.of(contestRating);
        }

        int totalSolved = easy + medium + hard;

        response.setSource("LIVE");
        response.setMessage("Fetched from LeetCode public GraphQL API");
        response.setBarLabels(List.of("Easy", "Medium", "Hard"));
        response.setBarData(List.of(easy, medium, hard));
        response.setLineLabels(lineLabels);
        response.setLineData(lineData);
        response.setProblemsAttempted(totalSolved);
        response.setContestStats(contests);
        response.setActivityStreak(Math.max(socialProfile.getDaysActive(), 0));

        socialProfile.setProblemsSolved(totalSolved);
        socialProfile.setContestRating(contestRating);
        socialProfile.setGlobalRank(globalRank);
        socialProfileRepository.save(socialProfile);

        return response;
    }

    private SocialAnalyticsResponse fromStoredAnalytics(SocialProfile socialProfile,
                                                        SocialAnalyticsResponse response,
                                                        String message) {

        response.setSource("STORED");
        response.setMessage(message);

        if (socialProfile.getPlatform() == Platform.GITHUB) {
            response.setBarLabels(List.of("Repositories", "Followers", "Commits"));
            response.setBarData(List.of(
                    safe(socialProfile.getRepositoriesCount()),
                    safe(socialProfile.getFollowers()),
                    safe(socialProfile.getTotalCommits())
            ));

            response.setLineLabels(List.of("Current"));
            response.setLineData(List.of(safe(socialProfile.getTotalCommits())));
            response.setProblemsAttempted(safe(socialProfile.getRepositoriesCount()));
            response.setContestStats(safe(socialProfile.getTotalCommits()));
            response.setActivityStreak(safe(socialProfile.getDaysActive()));
            return response;
        }

        response.setBarLabels(List.of("Problems Solved", "Contest Rating", "Global Rank"));
        response.setBarData(List.of(
                safe(socialProfile.getProblemsSolved()),
                safe(socialProfile.getContestRating()),
                safe(socialProfile.getGlobalRank())
        ));

        response.setLineLabels(List.of("Current"));
        response.setLineData(List.of(safe(socialProfile.getContestRating())));
        response.setProblemsAttempted(safe(socialProfile.getProblemsSolved()));
        response.setContestStats(safe(socialProfile.getContestRating()));
        response.setActivityStreak(safe(socialProfile.getDaysActive()));

        return response;
    }

    private int computeStreak(Set<LocalDate> activeDays) {
        if (activeDays.isEmpty()) {
            return 0;
        }

        int streak = 0;
        LocalDate cursor = LocalDate.now();

        while (activeDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        return streak;
    }

    private String resolveUsername(SocialProfile socialProfile) {
        if (socialProfile.getUsername() != null && !socialProfile.getUsername().trim().isEmpty()) {
            return socialProfile.getUsername().trim().replace("@", "");
        }

        String url = socialProfile.getProfileUrl();
        if (url == null || url.trim().isEmpty()) {
            return "";
        }

        String cleaned = url.trim();
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash == cleaned.length() - 1) {
            return cleaned;
        }

        return cleaned.substring(lastSlash + 1).replace("@", "");
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private Platform parsePlatform(String platformKey) {
        if (platformKey == null || platformKey.trim().isEmpty()) {
            return null;
        }

        String normalized = platformKey.trim().toUpperCase().replace(" ", "");

        switch (normalized) {
            case "LINKEDIN":
                return Platform.LINKEDIN;
            case "GITHUB":
                return Platform.GITHUB;
            case "LEETCODE":
                return Platform.LEETCODE;
            case "CODECHEF":
                return Platform.CODECHEF;
            case "HACKERRANK":
                return Platform.HACKERRANK;
            default:
                return null;
        }
    }

    private String toDisplayPlatform(Platform platform) {
        switch (platform) {
            case LINKEDIN:
                return "LinkedIn";
            case GITHUB:
                return "GitHub";
            case LEETCODE:
                return "LeetCode";
            case CODECHEF:
                return "CodeChef";
            case HACKERRANK:
                return "HackerRank";
            default:
                return platform.name();
        }
    }
}

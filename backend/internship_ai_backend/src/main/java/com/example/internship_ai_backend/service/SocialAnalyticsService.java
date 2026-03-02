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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
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
                    return loadHackerRankAnalytics(socialProfile, response);
                case CODECHEF:
                    return loadCodeChefAnalytics(socialProfile, response);
                case LINKEDIN:
                    return loadLinkedInAnalytics(socialProfile, response);
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

        Map<LocalDate, Integer> dailyActivity = fetchGitHubContributionHeatmap(username);
        int totalCommits = dailyActivity.values().stream().mapToInt(Integer::intValue).sum();
        Set<LocalDate> activeDays = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, Integer> entry : dailyActivity.entrySet()) {
            if (entry.getValue() > 0) {
                activeDays.add(entry.getKey());
            }
        }

        YearMonth currentMonth = YearMonth.now();
        LinkedHashMap<YearMonth, Integer> monthlyActivity = new LinkedHashMap<>();
        for (int i = 4; i >= 0; i--) {
            monthlyActivity.put(currentMonth.minusMonths(i), 0);
        }

        for (Map.Entry<LocalDate, Integer> entry : dailyActivity.entrySet()) {
            YearMonth ym = YearMonth.from(entry.getKey());
            if (monthlyActivity.containsKey(ym)) {
                monthlyActivity.put(ym, monthlyActivity.get(ym) + entry.getValue());
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
        response.setMessage("Fetched from GitHub contribution graph (last 365 days)");
        response.setBarLabels(List.of("Repositories", "Followers", "Following"));
        response.setBarData(List.of(repos, followers, following));
        response.setLineLabels(lineLabels);
        response.setLineData(lineData);
        response.setProblemsAttempted(repos);
        response.setContestStats(totalCommits);
        response.setActivityStreak(streak);
        response.setHighlights(buildGithubHighlights(username, repos, followers, totalCommits, streak));
        response.setHeatmap(buildLastYearHeatmap(dailyActivity));

        socialProfile.setRepositoriesCount(repos);
        socialProfile.setFollowers(followers);
        socialProfile.setTotalCommits(totalCommits);
        socialProfile.setDaysActive(streak);
        socialProfileRepository.save(socialProfile);

        return response;
    }

    private Map<LocalDate, Integer> fetchGitHubContributionHeatmap(String username) {
        LinkedHashMap<LocalDate, Integer> fallback = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(364);
        LocalDate end = LocalDate.now();

        for (int i = 0; i < 365; i++) {
            LocalDate day = start.plusDays(i);
            fallback.put(day, 0);
        }

        try {
            String contributionsUrl = "https://github.com/users/" + username + "/contributions?from=" + start + "&to=" + end;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(contributionsUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "text/html")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return fallback;
            }

            Map<LocalDate, Integer> parsed = parseGitHubContributionSvg(response.body());
            for (Map.Entry<LocalDate, Integer> entry : parsed.entrySet()) {
                if (!entry.getKey().isBefore(start) && !entry.getKey().isAfter(end)) {
                    fallback.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ignored) {
            return fallback;
        }

        return fallback;
    }

    private Map<LocalDate, Integer> parseGitHubContributionSvg(String svg) {
        LinkedHashMap<LocalDate, Integer> daily = new LinkedHashMap<>();
        if (svg == null || svg.isBlank()) {
            return daily;
        }

        // Try parsing from tooltip text (current GitHub format: "N contributions on DATE" or "No contributions on DATE")
        Pattern tooltipPattern = Pattern.compile(
            "<tool-tip[^>]+for=\"[^\"]*\"[^>]*>(\\d+)\\s+contributions? on\\s+([A-Za-z]+\\s+\\d+(?:st|nd|rd|th)?)</tool-tip>",
            Pattern.CASE_INSENSITIVE
        );
        Matcher tooltipMatcher = tooltipPattern.matcher(svg);
        while (tooltipMatcher.find()) {
            try {
                int count = Integer.parseInt(tooltipMatcher.group(1));
                String dateStr = tooltipMatcher.group(2);
                // Try to parse flexible date formats like "March 2nd", "Mar 2nd", etc.
                LocalDate date = parseFlexibleDate(dateStr);
                if (date != null) {
                    daily.put(date, count);
                }
            } catch (Exception ignored) {
            }
        }

        // Also handle "No contributions on DATE" format
        Pattern noContribPattern = Pattern.compile(
            "<tool-tip[^>]+for=\"[^\"]*\"[^>]*)?>No contributions on\\s+([A-Za-z]+\\s+\\d+(?:st|nd|rd|th)?)</tool-tip>",
            Pattern.CASE_INSENSITIVE
        );
        Matcher noContribMatcher = noContribPattern.matcher(svg);
        while (noContribMatcher.find()) {
            try {
                String dateStr = noContribMatcher.group(1);
                LocalDate date = parseFlexibleDate(dateStr);
                if (date != null && !daily.containsKey(date)) {
                    daily.put(date, 0);
                }
            } catch (Exception ignored) {
            }
        }

        if (!daily.isEmpty()) {
            return daily;
        }

        // Fallback to old data-count format for backward compatibility
        Pattern dateCountPattern = Pattern.compile("data-date=\"(\\d{4}-\\d{2}-\\d{2})\"[^>]*data-count=\"(\\d+)\"");
        Matcher matcher = dateCountPattern.matcher(svg);
        while (matcher.find()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group(1));
                int count = Integer.parseInt(matcher.group(2));
                daily.put(date, count);
            } catch (Exception ignored) {
            }
        }

        return daily;
    }

    private LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            // Format: "March 2nd" or "Mar 2" or similar
            // Strip ordinal indicators (st, nd, rd, th)
            String cleaned = dateStr.replaceAll("(?:st|nd|rd|th)", "").trim();
            
            // Get current year as reference
            int year = LocalDate.now().getYear();
            
            // Try parsing with different formats
            java.text.SimpleDateFormat[] formats = {
                new java.text.SimpleDateFormat("MMMM d", java.util.Locale.ENGLISH),
                new java.text.SimpleDateFormat("MMM d", java.util.Locale.ENGLISH),
            };
            
            for (java.text.SimpleDateFormat format : formats) {
                try {
                    java.util.Date parsed = format.parse(cleaned);
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(parsed);
                    cal.set(java.util.Calendar.YEAR, year);
                    
                    return LocalDate.ofInstant(
                        cal.toInstant(),
                        java.time.ZoneId.systemDefault()
                    );
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return null;
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
            "userProfileCalendar(username: $username) { streak totalActiveDays submissionCalendar } " +
            "recentAcSubmissionList(username: $username, limit: 20) { title titleSlug timestamp } " +
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

        JsonNode profileCalendarNode = dataNode.path("userProfileCalendar");
        int totalActiveDays = profileCalendarNode.path("totalActiveDays").asInt(0);
        int maxStreak = profileCalendarNode.path("streak").asInt(0);
        String submissionCalendarRaw = profileCalendarNode.path("submissionCalendar").asText("");
        Map<String, Integer> leetCodeHeatmap = parseSubmissionCalendarHeatmap(submissionCalendarRaw);

        if ((totalActiveDays == 0 || maxStreak == 0) && profileCalendarNode.has("submissionCalendar")) {
            int[] fallbackCalendarStats = parseCalendarStats(submissionCalendarRaw);
            if (totalActiveDays == 0) {
                totalActiveDays = fallbackCalendarStats[0];
            }
            if (maxStreak == 0) {
                maxStreak = fallbackCalendarStats[1];
            }
        }

        List<String> lineLabels = new ArrayList<>();
        List<Integer> lineData = new ArrayList<>();

        JsonNode historyArray = dataNode.path("userContestRankingHistory");
        int highestRating = contestRating;
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
                    highestRating = Math.max(highestRating, rating);
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
        response.setActivityStreak(maxStreak);
        response.setHeatmap(leetCodeHeatmap);
        response.setHighlights(buildLeetCodeHighlights(
            username,
            totalSolved,
            contests,
            highestRating,
            contestRating,
            totalActiveDays,
            maxStreak,
            globalRank
        ));

        List<String> solvedQuestions = extractLeetCodeSolvedQuestions(dataNode.path("recentAcSubmissionList"));
        response.setSolvedQuestions(solvedQuestions);
        response.setSolvedQuestionsNote(solvedQuestions.isEmpty()
            ? "No recent solved questions were found on this public profile."
            : "Recent accepted LeetCode questions");

        socialProfile.setProblemsSolved(totalSolved);
        socialProfile.setContestRating(contestRating);
        socialProfile.setGlobalRank(globalRank);
        socialProfile.setDaysActive(totalActiveDays);
        socialProfileRepository.save(socialProfile);

        return response;
    }

    private SocialAnalyticsResponse loadHackerRankAnalytics(SocialProfile socialProfile,
                                                            SocialAnalyticsResponse response) throws Exception {
        String username = resolveUsername(socialProfile);
        if (username.isEmpty()) {
            SocialAnalyticsResponse fallback = fromStoredAnalytics(
                    socialProfile,
                    response,
                    "Unable to resolve HackerRank username from saved profile link."
            );
            fallback.setSolvedQuestionsNote("HackerRank solved question list unavailable.");
            return fallback;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.hackerrank.com/rest/hackers/" + username + "/recent_challenges?offset=0&limit=20"))
                .header("Accept", "application/json")
                .header("User-Agent", "ai-internship-platform")
                .GET()
                .build();

        HttpResponse<String> apiResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (apiResponse.statusCode() >= 400) {
            SocialAnalyticsResponse fallback = fromStoredAnalytics(
                    socialProfile,
                    response,
                    "HackerRank recent challenges could not be fetched right now."
            );
            fallback.setSolvedQuestionsNote("HackerRank solved question list unavailable currently.");
            return fallback;
        }

        JsonNode root = objectMapper.readTree(apiResponse.body());
        JsonNode models = root.path("models");

        List<String> solvedQuestions = new ArrayList<>();
        if (models.isArray()) {
            for (JsonNode item : models) {
                String title = item.path("name").asText("");
                if (!title.isBlank()) {
                    solvedQuestions.add(title);
                }
                if (solvedQuestions.size() >= 20) {
                    break;
                }
            }
        }

        response.setSource("LIVE");
        response.setMessage("Fetched from HackerRank public endpoint");
        response.setSolvedQuestions(solvedQuestions);
        response.setSolvedQuestionsNote(solvedQuestions.isEmpty()
                ? "No recent solved questions found for this profile."
                : "Recent HackerRank challenges");

        response.setBarLabels(List.of("Solved Questions", "Current Rating", "Activity Days"));
        response.setBarData(List.of(
                solvedQuestions.size(),
                safe(socialProfile.getContestRating()),
                safe(socialProfile.getDaysActive())
        ));
        response.setLineLabels(List.of("Current"));
        response.setLineData(List.of(safe(socialProfile.getContestRating())));
        response.setHighlights(buildCodingHighlights(
                username,
                safe(socialProfile.getProblemsSolved()),
                0,
                safe(socialProfile.getContestRating()),
                safe(socialProfile.getContestRating())
        ));
        response.setHeatmap(new LinkedHashMap<>());

        return response;
    }

    private SocialAnalyticsResponse loadCodeChefAnalytics(SocialProfile socialProfile,
                                                          SocialAnalyticsResponse response) {
        SocialAnalyticsResponse fallback = fromStoredAnalytics(
                socialProfile,
                response,
                "CodeChef public API does not reliably expose solved question list without authenticated scraping."
        );
        fallback.setSolvedQuestions(new ArrayList<>());
        fallback.setSolvedQuestionsNote("CodeChef solved-question list is not publicly available via stable API in current setup.");
        return fallback;
    }

    private SocialAnalyticsResponse loadLinkedInAnalytics(SocialProfile socialProfile,
                                                          SocialAnalyticsResponse response) {
        String profileUrl = socialProfile.getProfileUrl();
        String username = extractLinkedInPublicId(profileUrl, resolveUsername(socialProfile));

        if (profileUrl == null || profileUrl.isBlank()) {
            SocialAnalyticsResponse fallback = fromStoredAnalytics(
                    socialProfile,
                    response,
                    "LinkedIn profile URL is missing."
            );
            fallback.setUsername(username);
            fallback.setProfilePhotoUrl(buildProfilePhotoFallback(username));
            fallback.setLastPost("Last post content is not exposed by LinkedIn public APIs in this setup.");
            return fallback;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(profileUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();

            HttpResponse<String> pageResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String effectiveProfileUrl = pageResponse.uri() != null
                    ? pageResponse.uri().toString()
                    : profileUrl;

            String resolvedUsername = extractLinkedInPublicId(effectiveProfileUrl, username);
            if (!resolvedUsername.isBlank()) {
                username = resolvedUsername;
            }

            if (pageResponse.statusCode() >= 400) {
                SocialAnalyticsResponse fallback = fromStoredAnalytics(
                        socialProfile,
                        response,
                        "LinkedIn blocked public profile fetch; showing stored values."
                );
                fallback.setProfileUrl(effectiveProfileUrl);
                fallback.setUsername(username);
                fallback.setProfilePhotoUrl(buildProfilePhotoFallback(username));
                fallback.setLastPost("Last post content is not exposed by LinkedIn public APIs in this setup.");
                return fallback;
            }

            String html = pageResponse.body();
            int liveConnections = extractLinkedInConnections(html, safe(socialProfile.getConnections()));
            int livePosts = extractLinkedInPosts(html, safe(socialProfile.getPosts()));

            socialProfile.setProfileUrl(effectiveProfileUrl);
            socialProfile.setUsername(username);
            socialProfile.setConnections(liveConnections);
            socialProfile.setPosts(livePosts);
            socialProfileRepository.save(socialProfile);

            response.setSource("LIVE");
            response.setMessage("Fetched from public LinkedIn profile page");
            response.setProfileUrl(effectiveProfileUrl);
            response.setBarLabels(List.of());
            response.setBarData(List.of());
            response.setLineLabels(List.of());
            response.setLineData(List.of());
            response.setHeatmap(new LinkedHashMap<>());
            response.setUsername(username);
            response.setProfilePhotoUrl(buildProfilePhotoFallback(username));
            response.setTotalConnections(liveConnections);
            response.setTotalPosts(livePosts);
            response.setLastPost("Last post content is not exposed by LinkedIn public APIs in this setup.");
            response.setHighlights(buildLinkedInHighlights(username, liveConnections, livePosts));

            return response;
        } catch (Exception ex) {
            SocialAnalyticsResponse fallback = fromStoredAnalytics(
                    socialProfile,
                    response,
                    "Could not parse LinkedIn profile right now; showing stored values."
            );
            fallback.setUsername(username);
            fallback.setProfilePhotoUrl(buildProfilePhotoFallback(username));
            fallback.setLastPost("Last post content is not exposed by LinkedIn public APIs in this setup.");
            return fallback;
        }
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
                response.setHighlights(buildGithubHighlights(
                    resolveUsername(socialProfile),
                    safe(socialProfile.getRepositoriesCount()),
                    safe(socialProfile.getFollowers()),
                    safe(socialProfile.getTotalCommits()),
                    safe(socialProfile.getDaysActive())
                ));
                    response.setHeatmap(new LinkedHashMap<>());
            return response;
        }

            if (socialProfile.getPlatform() == Platform.LINKEDIN) {
                response.setBarLabels(List.of());
                response.setBarData(List.of());
                response.setLineLabels(List.of());
                response.setLineData(List.of());
                response.setProblemsAttempted(safe(socialProfile.getConnections()));
                response.setContestStats(safe(socialProfile.getPosts()));
                response.setActivityStreak(safe(socialProfile.getDaysActive()));

                String username = resolveUsername(socialProfile);
                response.setUsername(username);
                response.setTotalConnections(safe(socialProfile.getConnections()));
                response.setTotalPosts(safe(socialProfile.getPosts()));
                response.setProfilePhotoUrl(buildProfilePhotoFallback(username));
                response.setLastPost("Last post content is not exposed by LinkedIn public APIs in this setup.");
                response.setHighlights(buildLinkedInHighlights(
                    username,
                    safe(socialProfile.getConnections()),
                    safe(socialProfile.getPosts())
                ));
                response.setHeatmap(new LinkedHashMap<>());
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
        response.setHighlights(buildCodingHighlights(
                resolveUsername(socialProfile),
                safe(socialProfile.getProblemsSolved()),
                0,
                safe(socialProfile.getContestRating()),
                safe(socialProfile.getContestRating())
        ));
        response.setHeatmap(new LinkedHashMap<>());
        response.setMessage(message + " Save/refresh this platform data source to improve metrics.");

        return response;
    }

    private Map<String, String> buildLinkedInHighlights(String username, int connections, int posts) {
        LinkedHashMap<String, String> highlights = new LinkedHashMap<>();
        highlights.put("Username", username.isBlank() ? "N/A" : username);
        highlights.put("Connections", String.valueOf(connections));
        highlights.put("Posts", String.valueOf(posts));
        return highlights;
    }

    private Map<String, String> buildCodingHighlights(String username,
                                                      int problemsSolved,
                                                      int contestsAttempted,
                                                      int highestRating,
                                                      int currentRating) {
        LinkedHashMap<String, String> highlights = new LinkedHashMap<>();
        highlights.put("Username", username.isBlank() ? "N/A" : username);
        highlights.put("Problems Solved", String.valueOf(problemsSolved));
        highlights.put("Contests Attempted", String.valueOf(contestsAttempted));
        highlights.put("Highest Rating", String.valueOf(highestRating));
        highlights.put("Current Rating", String.valueOf(currentRating));
        return highlights;
    }

    private Map<String, String> buildLeetCodeHighlights(String username,
                                                        int problemsSolved,
                                                        int contestsAttempted,
                                                        int highestRating,
                                                        int currentRating,
                                                        int totalActiveDays,
                                                        int maxStreak,
                                                        int globalRank) {
        LinkedHashMap<String, String> highlights = new LinkedHashMap<>();
        highlights.put("Username", username.isBlank() ? "N/A" : username);
        highlights.put("Problems Solved", String.valueOf(problemsSolved));
        highlights.put("Contests Attempted", String.valueOf(contestsAttempted));
        highlights.put("Highest Rating", String.valueOf(highestRating));
        highlights.put("Current Rating", String.valueOf(currentRating));
        highlights.put("Global Rank", String.valueOf(globalRank));
        highlights.put("Total Active Days", String.valueOf(totalActiveDays));
        highlights.put("Max Streak", String.valueOf(maxStreak));
        return highlights;
    }

    private int[] parseCalendarStats(String submissionCalendarRaw) {
        int totalActiveDays = 0;
        int maxStreak = 0;

        if (submissionCalendarRaw == null || submissionCalendarRaw.isBlank()) {
            return new int[]{0, 0};
        }

        try {
            JsonNode calendarNode = objectMapper.readTree(submissionCalendarRaw);
            if (!calendarNode.isObject()) {
                return new int[]{0, 0};
            }

            TreeSet<LocalDate> activeDates = new TreeSet<>();
            calendarNode.properties().forEach(entry -> {
                int count = entry.getValue().asInt(0);
                if (count <= 0) {
                    return;
                }

                try {
                    long epochSeconds = Long.parseLong(entry.getKey());
                    LocalDate day = java.time.Instant.ofEpochSecond(epochSeconds)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                    activeDates.add(day);
                } catch (Exception ignored) {
                }
            });

            totalActiveDays = activeDates.size();

            LocalDate previous = null;
            int running = 0;
            for (LocalDate day : activeDates) {
                if (previous != null && previous.plusDays(1).equals(day)) {
                    running++;
                } else {
                    running = 1;
                }

                if (running > maxStreak) {
                    maxStreak = running;
                }

                previous = day;
            }
        } catch (Exception ignored) {
            return new int[]{0, 0};
        }

        return new int[]{totalActiveDays, maxStreak};
    }

    private int extractLinkedInConnections(String html, int fallback) {
        if (html == null || html.isBlank()) {
            return fallback;
        }

        Pattern[] patterns = new Pattern[] {
                Pattern.compile("(\\d[\\d,]*)\\s*\\+?\\s*connections", Pattern.CASE_INSENSITIVE),
                Pattern.compile("connections\\D{0,20}(\\d[\\d,]*)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return parseIntSafe(matcher.group(1), fallback);
            }
        }

        return fallback;
    }

    private int extractLinkedInPosts(String html, int fallback) {
        if (html == null || html.isBlank()) {
            return fallback;
        }

        Pattern[] patterns = new Pattern[] {
                Pattern.compile("(\\d[\\d,]*)\\s*posts", Pattern.CASE_INSENSITIVE),
                Pattern.compile("posts\\D{0,20}(\\d[\\d,]*)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return parseIntSafe(matcher.group(1), fallback);
            }
        }

        return fallback;
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String extractLinkedInPublicId(String profileUrl, String fallback) {
        if (profileUrl == null || profileUrl.isBlank()) {
            return (fallback == null || fallback.isBlank()) ? "" : fallback;
        }

        try {
            String normalized = profileUrl.trim();
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://" + normalized;
            }

            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return (fallback == null || fallback.isBlank()) ? "" : fallback;
            }

            String[] parts = path.split("/");
            for (int index = 0; index < parts.length - 1; index++) {
                if ("in".equalsIgnoreCase(parts[index]) && !parts[index + 1].isBlank()) {
                    return parts[index + 1].trim();
                }
            }
        } catch (Exception ignored) {
        }

        return (fallback == null || fallback.isBlank()) ? "" : fallback;
    }

    private Map<String, Integer> parseSubmissionCalendarHeatmap(String submissionCalendarRaw) {
        Map<LocalDate, Integer> dailyCounts = new LinkedHashMap<>();

        if (submissionCalendarRaw == null || submissionCalendarRaw.isBlank()) {
            return buildLastYearHeatmap(dailyCounts);
        }

        try {
            JsonNode calendarNode = objectMapper.readTree(submissionCalendarRaw);
            if (!calendarNode.isObject()) {
                return buildLastYearHeatmap(dailyCounts);
            }

            calendarNode.properties().forEach(entry -> {
                int count = entry.getValue().asInt(0);
                if (count < 0) {
                    return;
                }

                try {
                    long epochSeconds = Long.parseLong(entry.getKey());
                    LocalDate day = java.time.Instant.ofEpochSecond(epochSeconds)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                    dailyCounts.put(day, count);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
            return buildLastYearHeatmap(new LinkedHashMap<>());
        }

        return buildLastYearHeatmap(dailyCounts);
    }

    private Map<String, Integer> buildLastYearHeatmap(Map<LocalDate, Integer> dailyCounts) {
        LinkedHashMap<String, Integer> heatmap = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(364);

        for (int i = 0; i < 365; i++) {
            LocalDate day = start.plusDays(i);
            int count = dailyCounts.getOrDefault(day, 0);
            heatmap.put(day.toString(), count);
        }

        return heatmap;
    }

    private List<String> extractLeetCodeSolvedQuestions(JsonNode recentAcNode) {
        LinkedHashSet<String> uniqueTitles = new LinkedHashSet<>();

        if (recentAcNode != null && recentAcNode.isArray()) {
            for (JsonNode item : recentAcNode) {
                String title = item.path("title").asText("");
                if (!title.isBlank()) {
                    uniqueTitles.add(title);
                }
            }
        }

        return new ArrayList<>(uniqueTitles);
    }

    private String buildProfilePhotoFallback(String username) {
        String safeName = (username == null || username.isBlank()) ? "LinkedIn User" : username;
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8);
        return "https://ui-avatars.com/api/?name=" + encoded + "&background=E2E8F0&color=1E293B&size=128";
    }

    private Map<String, String> buildGithubHighlights(String username,
                                                      int repositories,
                                                      int followers,
                                                      int commits,
                                                      int streak) {
        LinkedHashMap<String, String> highlights = new LinkedHashMap<>();
        highlights.put("Username", username.isBlank() ? "N/A" : username);
        highlights.put("Repositories", String.valueOf(repositories));
        highlights.put("Followers", String.valueOf(followers));
        highlights.put("Commits", String.valueOf(commits));
        highlights.put("Activity Streak", streak + " days");
        return highlights;
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

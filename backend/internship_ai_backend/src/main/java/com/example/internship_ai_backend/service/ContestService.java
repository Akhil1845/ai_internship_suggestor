package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.entity.ContestPreference;
import com.example.internship_ai_backend.repository.ContestPreferenceRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContestService {

    private static final Map<String, String> KONTESTS_URLS = new LinkedHashMap<>();
    private static final long CACHE_TTL_MS = 2 * 60 * 1000;

    static {
        KONTESTS_URLS.put("CODEFORCES", "https://kontests.net/api/v1/codeforces");
        KONTESTS_URLS.put("LEETCODE",   "https://kontests.net/api/v1/leet_code");
        KONTESTS_URLS.put("CODECHEF",   "https://kontests.net/api/v1/code_chef");
    }

    private final ContestPreferenceRepository preferenceRepository;
    private final RestTemplate restTemplate;
    private final Map<String, CachedContestData> contestCache = new ConcurrentHashMap<>();

    private static final class CachedContestData {
        private final List<Map<String, Object>> contests;
        private final long fetchedAt;

        private CachedContestData(List<Map<String, Object>> contests, long fetchedAt) {
            this.contests = contests;
            this.fetchedAt = fetchedAt;
        }
    }

    public ContestService(ContestPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Fetch upcoming contests: try kontests.net first, then platform-specific API. */
    public List<Map<String, Object>> getContests(String platform) {
        String key = platform.toUpperCase();
        long now = System.currentTimeMillis();

        CachedContestData cached = contestCache.get(key);
        if (cached != null && (now - cached.fetchedAt) < CACHE_TTL_MS) {
            return cached.contests;
        }

        // Attempt 1: kontests.net (with browser User-Agent to avoid blocking)
        String konUrl = KONTESTS_URLS.get(key);
        if (konUrl != null) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
                headers.set("Accept", "application/json");
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    konUrl, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                List<Map<String, Object>> result = response.getBody();
                if (result != null && !result.isEmpty()) {
                    contestCache.put(key, new CachedContestData(result, now));
                    return result;
                }
            } catch (Exception ignored) {}
        }

        // Attempt 2: platform-specific fallback
        List<Map<String, Object>> fallback;
        switch (key) {
            case "CODEFORCES": fallback = fetchCodeforces(); break;
            case "LEETCODE":   fallback = fetchLeetCode(); break;
            case "CODECHEF":   fallback = fetchCodeChef(); break;
            default:            fallback = Collections.emptyList();
        }

        if (!fallback.isEmpty()) {
            contestCache.put(key, new CachedContestData(fallback, now));
            return fallback;
        }

        // If fetch fails, return the last cached data (even if stale) as a fallback.
        if (cached != null) {
            return cached.contests;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCodeforces() {
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    "https://codeforces.com/api/contest.list?gym=false", Map.class);
            if (resp == null || !"OK".equals(resp.get("status"))) return Collections.emptyList();
            List<Map<String, Object>> all = (List<Map<String, Object>>) resp.get("result");
            if (all == null) return Collections.emptyList();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> c : all) {
                if (!"BEFORE".equals(c.get("phase"))) continue;
                if (out.size() >= 8) break;
                long start    = ((Number) c.get("startTimeSeconds")).longValue();
                long duration = ((Number) c.get("durationSeconds")).longValue();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name",       c.get("name"));
                item.put("start_time", Instant.ofEpochSecond(start).toString());
                item.put("end_time",   Instant.ofEpochSecond(start + duration).toString());
                item.put("url",        "https://codeforces.com/contest/" + c.get("id"));
                out.add(item);
            }
            return out;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchLeetCode() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Referer", "https://leetcode.com");
            String body = "{\"query\":\"{ upcomingContests { title titleSlug startTime duration } }\"}";
            ResponseEntity<Map<String, Object>> re = restTemplate.exchange(
                    "https://leetcode.com/graphql", HttpMethod.POST,
                    new HttpEntity<>(body, headers), (Class<Map<String, Object>>) (Class<?>) Map.class);
            Map<String, Object> resp = re.getBody();
            if (resp == null) return Collections.emptyList();
            Map<String, Object> data = (Map<String, Object>) resp.get("data");
            if (data == null) return Collections.emptyList();
            List<Map<String, Object>> contests = (List<Map<String, Object>>) data.get("upcomingContests");
            if (contests == null) return Collections.emptyList();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> c : contests) {
                long start    = ((Number) c.get("startTime")).longValue();
                long duration = ((Number) c.get("duration")).longValue();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name",       c.get("title"));
                item.put("start_time", Instant.ofEpochSecond(start).toString());
                item.put("end_time",   Instant.ofEpochSecond(start + duration).toString());
                item.put("url",        "https://leetcode.com/contest/" + c.get("titleSlug") + "/");
                out.add(item);
            }
            return out;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCodeChef() {
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    "https://www.codechef.com/api/list/contests/all?sort_by=START&sorting_order=asc&offset=0&mode=all",
                    Map.class);
            if (resp == null) return Collections.emptyList();
            List<Map<String, Object>> present = (List<Map<String, Object>>) resp.getOrDefault("present_contests", Collections.emptyList());
            List<Map<String, Object>> future  = (List<Map<String, Object>>) resp.getOrDefault("future_contests",  Collections.emptyList());
            List<Map<String, Object>> all = new ArrayList<>();
            all.addAll(present);
            all.addAll(future);
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < all.size() && out.size() < 8; i++) {
                Map<String, Object> c = all.get(i);
                String contestName = String.valueOf(c.getOrDefault("contest_name", ""));
                // Show only Starters contests
                if (!contestName.toLowerCase().contains("starters")) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name",       c.get("contest_name"));
                item.put("start_time", c.getOrDefault("contest_start_date_iso", c.get("contest_start_date")));
                item.put("end_time",   c.getOrDefault("contest_end_date_iso",   c.get("contest_end_date")));
                item.put("url",        "https://www.codechef.com/" + c.get("contest_code"));
                out.add(item);
            }
            return out;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    /** Return a map of platform → enabled for this student */
    public Map<String, Boolean> getPreferences(String email) {
        Map<String, Boolean> prefs = new LinkedHashMap<>();
        for (String platform : KONTESTS_URLS.keySet()) {
            prefs.put(platform, false);
        }
        List<ContestPreference> saved = preferenceRepository.findByStudentEmail(email);
        for (ContestPreference pref : saved) {
            String key = pref.getPlatform().toUpperCase();
            if (prefs.containsKey(key)) {
                prefs.put(key, pref.isEnabled());
            }
        }
        return prefs;
    }

    /** Upsert a single platform preference */
    @Transactional
    public void updatePreference(String email, String platform, boolean enabled) {
        String normalised = platform.toUpperCase();
        Optional<ContestPreference> existing =
                preferenceRepository.findByStudentEmailAndPlatform(email, normalised);

        ContestPreference pref = existing.orElseGet(ContestPreference::new);
        pref.setStudentEmail(email);
        pref.setPlatform(normalised);
        pref.setEnabled(enabled);
        preferenceRepository.save(pref);
    }
}

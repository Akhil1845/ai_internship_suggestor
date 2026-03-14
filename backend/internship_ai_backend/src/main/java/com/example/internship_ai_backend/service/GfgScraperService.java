package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.entity.InterviewTopic;
import com.example.internship_ai_backend.entity.InterviewTopic.Category;
import com.example.internship_ai_backend.entity.InterviewTopic.Company;
import com.example.internship_ai_backend.entity.InterviewTopic.Difficulty;
import com.example.internship_ai_backend.repository.InterviewTopicRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GfgScraperService {

    private static final Logger log = LoggerFactory.getLogger(GfgScraperService.class);

    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS  = 12_000;
    private static final int DELAY_MS    = 1_500;

    private final InterviewTopicRepository repo;

    public GfgScraperService(InterviewTopicRepository repo) {
        this.repo = repo;
    }

    /* ═══════════════════════════════════════════════════
       Seed definitions — each entry maps to one GFG URL
       ═══════════════════════════════════════════════════ */
    private static final List<SeedEntry> SEEDS = new ArrayList<>();

    static {
        // ── AMAZON ─────────────────────────────────────
        add("https://www.geeksforgeeks.org/amazon-interview-questions/",
            "Amazon Interview Questions & Experience", Category.DSA, Company.AMAZON, Difficulty.MEDIUM,
            "amazon,interview,array,string,tree,dp");
        add("https://www.geeksforgeeks.org/top-50-array-coding-problems-for-interviews/",
            "Top 50 Array Coding Problems for Interviews (Amazon)", Category.DSA, Company.AMAZON, Difficulty.MEDIUM,
            "array,sorting,binary search,amazon");
        add("https://www.geeksforgeeks.org/top-20-dynamic-programming-interview-questions/",
            "Top 20 Dynamic Programming Interview Questions (Amazon)", Category.DSA, Company.AMAZON, Difficulty.HARD,
            "dynamic programming,dp,amazon,recursion");

        // ── GOOGLE ─────────────────────────────────────
        add("https://www.geeksforgeeks.org/google-interview-questions/",
            "Google Interview Questions & Experience", Category.DSA, Company.GOOGLE, Difficulty.HARD,
            "google,interview,graph,tree,system design");
        add("https://www.geeksforgeeks.org/graph-data-structure-and-algorithms/",
            "Graph Data Structure & Algorithms (Google)", Category.DSA, Company.GOOGLE, Difficulty.HARD,
            "graph,bfs,dfs,dijkstra,google");
        add("https://www.geeksforgeeks.org/commonly-asked-algorithm-interview-questions-set-1/",
            "Commonly Asked Algorithm Interview Questions (Google)", Category.DSA, Company.GOOGLE, Difficulty.MEDIUM,
            "algorithm,sorting,searching,google");

        // ── MICROSOFT ──────────────────────────────────
        add("https://www.geeksforgeeks.org/microsoft-interview-questions/",
            "Microsoft Interview Questions & Experience", Category.DSA, Company.MICROSOFT, Difficulty.MEDIUM,
            "microsoft,interview,linked list,tree,array");
        add("https://www.geeksforgeeks.org/top-10-algorithms-in-interview-questions/",
            "Top 10 Algorithms for Interviews (Microsoft)", Category.DSA, Company.MICROSOFT, Difficulty.MEDIUM,
            "algorithm,graph,dp,greedy,microsoft");
        add("https://www.geeksforgeeks.org/commonly-asked-oop-interview-questions/",
            "OOP Interview Questions (Microsoft)", Category.JAVA, Company.MICROSOFT, Difficulty.EASY,
            "oop,object oriented,class,inheritance,microsoft");

        // ── FLIPKART ───────────────────────────────────
        add("https://www.geeksforgeeks.org/flipkart-interview-questions/",
            "Flipkart Interview Questions & Experience", Category.DSA, Company.FLIPKART, Difficulty.MEDIUM,
            "flipkart,interview,array,tree,string");
        add("https://www.geeksforgeeks.org/top-50-tree-coding-problems-for-interviews/",
            "Top 50 Tree Coding Problems (Flipkart)", Category.DSA, Company.FLIPKART, Difficulty.MEDIUM,
            "tree,binary tree,bst,flipkart");

        // ── INFOSYS ────────────────────────────────────
        add("https://www.geeksforgeeks.org/infosys-interview-questions/",
            "Infosys Interview Questions & Experience", Category.APTITUDE, Company.INFOSYS, Difficulty.EASY,
            "infosys,interview,aptitude,reasoning,verbal");
        add("https://www.geeksforgeeks.org/commonly-asked-dbms-interview-questions/",
            "Commonly Asked DBMS Interview Questions (Infosys)", Category.DBMS, Company.INFOSYS, Difficulty.EASY,
            "dbms,sql,normalization,transaction,infosys");
        add("https://www.geeksforgeeks.org/commonly-asked-java-programming-interview-questions-set-1/",
            "Java Programming Interview Questions Set 1 (Infosys)", Category.JAVA, Company.INFOSYS, Difficulty.EASY,
            "java,oops,collections,infosys");

        // ── TCS ────────────────────────────────────────
        add("https://www.geeksforgeeks.org/tcs-interview-questions/",
            "TCS Interview Questions & Experience", Category.APTITUDE, Company.TCS, Difficulty.EASY,
            "tcs,interview,aptitude,verbal,reasoning");
        add("https://www.geeksforgeeks.org/sql-interview-questions/",
            "SQL Interview Questions (TCS)", Category.DBMS, Company.TCS, Difficulty.EASY,
            "sql,query,dbms,tcs,database");
        add("https://www.geeksforgeeks.org/c-plus-plus-interview-questions/",
            "C++ Interview Questions (TCS)", Category.JAVA, Company.TCS, Difficulty.EASY,
            "cpp,c++,oops,tcs");

        // ── WIPRO ──────────────────────────────────────
        add("https://www.geeksforgeeks.org/wipro-interview-questions/",
            "Wipro Interview Questions & Experience", Category.APTITUDE, Company.WIPRO, Difficulty.EASY,
            "wipro,interview,aptitude,verbal,reasoning");
        add("https://www.geeksforgeeks.org/operating-systems-interview-questions/",
            "OS Interview Questions (Wipro)", Category.OS, Company.WIPRO, Difficulty.EASY,
            "os,operating system,process,thread,wipro");

        // ── ACCENTURE ──────────────────────────────────
        add("https://www.geeksforgeeks.org/accenture-interview-questions/",
            "Accenture Interview Questions & Experience", Category.APTITUDE, Company.ACCENTURE, Difficulty.EASY,
            "accenture,interview,aptitude,communication");
        add("https://www.geeksforgeeks.org/commonly-asked-computer-networks-interview-questions/",
            "Computer Networks Interview Questions (Accenture)", Category.CN, Company.ACCENTURE, Difficulty.EASY,
            "computer networks,tcp,ip,osi,accenture");

        // ── COGNIZANT ──────────────────────────────────
        add("https://www.geeksforgeeks.org/cognizant-interview-questions/",
            "Cognizant Interview Questions & Experience", Category.APTITUDE, Company.COGNIZANT, Difficulty.EASY,
            "cognizant,interview,aptitude,verbal");

        // ── CAPGEMINI ──────────────────────────────────
        add("https://www.geeksforgeeks.org/capgemini-interview-questions/",
            "Capgemini Interview Questions & Experience", Category.APTITUDE, Company.CAPGEMINI, Difficulty.EASY,
            "capgemini,interview,aptitude,pseudocode");

        // ── GENERAL — Java ─────────────────────────────
        add("https://www.geeksforgeeks.org/commonly-asked-java-programming-interview-questions-set-2/",
            "Java Programming Interview Questions Set 2", Category.JAVA, Company.GENERAL, Difficulty.MEDIUM,
            "java,collections,generics,exception,multithreading");
        add("https://www.geeksforgeeks.org/java-multithreading-interview-questions/",
            "Java Multithreading Interview Questions", Category.JAVA, Company.GENERAL, Difficulty.HARD,
            "java,multithreading,thread,synchronization,concurrency");
        add("https://www.geeksforgeeks.org/java-collections-interview-questions/",
            "Java Collections Interview Questions", Category.JAVA, Company.GENERAL, Difficulty.MEDIUM,
            "java,collections,list,map,set,hashmap");
        add("https://www.geeksforgeeks.org/java-8-interview-questions/",
            "Java 8 Interview Questions (Streams, Lambdas)", Category.JAVA, Company.GENERAL, Difficulty.MEDIUM,
            "java 8,stream,lambda,functional,optional");

        // ── GENERAL — DSA ──────────────────────────────
        add("https://www.geeksforgeeks.org/commonly-asked-data-structures-interview-questions-set-1/",
            "Commonly Asked Data Structure Interview Questions", Category.DSA, Company.GENERAL, Difficulty.MEDIUM,
            "data structure,array,linked list,stack,queue");
        add("https://www.geeksforgeeks.org/top-50-string-coding-problems-for-interviews/",
            "Top 50 String Coding Problems for Interviews", Category.DSA, Company.GENERAL, Difficulty.MEDIUM,
            "string,palindrome,anagram,substring,dsa");

        // ── GENERAL — DBMS ─────────────────────────────
        add("https://www.geeksforgeeks.org/commonly-asked-dbms-interview-questions-set-2/",
            "Commonly Asked DBMS Interview Questions Set 2", Category.DBMS, Company.GENERAL, Difficulty.MEDIUM,
            "dbms,normalization,acid,sql,transaction");
        add("https://www.geeksforgeeks.org/sql-query-interview-questions/",
            "SQL Query Interview Questions", Category.DBMS, Company.GENERAL, Difficulty.MEDIUM,
            "sql,query,join,subquery,aggregate");

        // ── GENERAL — OS ───────────────────────────────
        add("https://www.geeksforgeeks.org/os-interview-questions-and-answers-set-2/",
            "OS Interview Questions Set 2 — Scheduling, Memory", Category.OS, Company.GENERAL, Difficulty.MEDIUM,
            "os,process scheduling,memory management,paging,segmentation");
        add("https://www.geeksforgeeks.org/deadlock-interview-questions/",
            "Deadlock Interview Questions", Category.OS, Company.GENERAL, Difficulty.MEDIUM,
            "deadlock,mutex,semaphore,os,process");

        // ── GENERAL — CN ───────────────────────────────
        add("https://www.geeksforgeeks.org/last-minute-notes-computer-network/",
            "Computer Networks – Last Minute Notes", Category.CN, Company.GENERAL, Difficulty.EASY,
            "cn,osi model,tcp,udp,ip,routing,computer networks");
        add("https://www.geeksforgeeks.org/http-vs-https-difference/",
            "HTTP vs HTTPS — Interview Concept", Category.CN, Company.GENERAL, Difficulty.EASY,
            "http,https,ssl,tls,protocol,cn");

        // ── GENERAL — HR ───────────────────────────────
        add("https://www.geeksforgeeks.org/hr-interview-questions-and-answers/",
            "HR Interview Questions & Answers", Category.HR, Company.GENERAL, Difficulty.EASY,
            "hr,interview,tell me about yourself,strengths,weaknesses");
        add("https://www.geeksforgeeks.org/behavioral-interview-questions-and-answers/",
            "Behavioral Interview Questions (HR)", Category.HR, Company.GENERAL, Difficulty.EASY,
            "hr,behavioral,situation,star method,communication");
        add("https://www.geeksforgeeks.org/group-discussion-topics/",
            "Group Discussion Topics for Interviews", Category.HR, Company.GENERAL, Difficulty.EASY,
            "group discussion,gd,communication,hr");

        // ── GENERAL — Aptitude ─────────────────────────
        add("https://www.geeksforgeeks.org/aptitude-questions-and-answers/",
            "Aptitude Questions & Answers for Placement", Category.APTITUDE, Company.GENERAL, Difficulty.EASY,
            "aptitude,quantitative,reasoning,time speed distance,percentage");
        add("https://www.geeksforgeeks.org/logical-reasoning-questions/",
            "Logical Reasoning Questions for Placement", Category.APTITUDE, Company.GENERAL, Difficulty.EASY,
            "reasoning,logical,coding decoding,blood relation,aptitude");
    }

    private static void add(String url, String title, Category cat, Company com, Difficulty diff, String kw) {
        SEEDS.add(new SeedEntry(url, title, cat, com, diff, kw));
    }

    /* ═══════════════════════════════════════════════════
       Public entry points
       ═══════════════════════════════════════════════════ */

    /** Admin manual trigger */
    public int triggerScrape() {
        log.info("Manual scrape triggered");
        return runScrape();
    }

    /** Weekly Sunday 2 AM auto re-scrape */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void scheduledScrape() {
        log.info("Scheduled weekly scrape starting…");
        runScrape();
    }

    /* ═══════════════════════════════════════════════════
       Core scraping loop
       ═══════════════════════════════════════════════════ */

    private int runScrape() {
        int saved = 0;
        for (SeedEntry seed : SEEDS) {
            try {
                if (repo.findBySourceUrl(seed.url).isPresent()) {
                    log.debug("Skipping (already stored): {}", seed.url);
                    continue;
                }
                String content = fetchContent(seed.url);
                if (content == null || content.isBlank()) {
                    log.warn("Empty content for {}", seed.url);
                    content = fallbackDescription(seed);
                }
                InterviewTopic topic = new InterviewTopic();
                topic.setTitle(seed.title);
                topic.setContent(content);
                topic.setCategory(seed.category);
                topic.setCompany(seed.company);
                topic.setDifficulty(seed.difficulty);
                topic.setKeywords(seed.keywords);
                topic.setSourceUrl(seed.url);
                topic.setScrapedAt(LocalDateTime.now());
                topic.setTimesViewed(0);
                repo.save(topic);
                saved++;
                log.info("Saved topic: {}", seed.title);
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("Error scraping {}: {}", seed.url, ex.getMessage());
            }
        }
        log.info("Scrape complete — {} topics saved", saved);
        return saved;
    }

    /* ═══════════════════════════════════════════════════
       Jsoup fetcher — tries multiple known GFG selectors
       ═══════════════════════════════════════════════════ */

    private String fetchContent(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(TIMEOUT_MS)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get();

            // Try the most curent GFG article body selectors in priority order
            String[] selectors = {
                "article.content",
                "div.article-body",
                "div[class^=article_body]",
                "div.entry-content",
                "div.text",
                "div#post-content",
                "div.post-content",
                "main article",
                "div.markdown"
            };

            for (String sel : selectors) {
                Element el = doc.selectFirst(sel);
                if (el != null && !el.text().isBlank()) {
                    // Clean: keep only allowed inline tags, strip scripts/ads
                    String cleaned = Jsoup.clean(el.outerHtml(),
                            Safelist.relaxed()
                                    .addTags("h1","h2","h3","h4","h5","h6","pre","code"));
                    if (cleaned.length() > 200) return cleaned;
                }
            }

            // Fallback: grab all <p> tags from the page body
            StringBuilder sb = new StringBuilder();
            doc.select("p").stream()
               .filter(p -> p.text().length() > 60)
               .limit(30)
               .forEach(p -> sb.append("<p>").append(p.text()).append("</p>\n"));
            return sb.length() > 200 ? sb.toString() : null;

        } catch (Exception e) {
            log.warn("Jsoup fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** When scraping yields nothing, store a rich placeholder so the frontend
     *  still has useful content describing what the topic covers. */
    private String fallbackDescription(SeedEntry s) {
        return "<p><strong>" + s.title + "</strong></p>" +
               "<p>This topic covers key interview questions and concepts related to <em>" +
               s.keywords.replace(",", ", ") + "</em>. " +
               "Visit the source article on GeeksForGeeks for detailed explanations, " +
               "code examples, and practice problems.</p>" +
               "<p>Category: " + s.category.name() +
               " | Company focus: " + s.company.name() +
               " | Difficulty: " + s.difficulty.name() + "</p>";
    }

    /* ═══════════════════════════════════════════════════
       Seed record
       ═══════════════════════════════════════════════════ */

    private record SeedEntry(
        String url,
        String title,
        Category category,
        Company company,
        Difficulty difficulty,
        String keywords
    ) {}
}

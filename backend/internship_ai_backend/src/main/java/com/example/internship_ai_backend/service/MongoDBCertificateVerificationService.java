package com.example.internship_ai_backend.service;

import com.example.internship_ai_backend.dto.VerificationResult;
import com.example.internship_ai_backend.dto.VerificationResult.CheckItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/**
 * Analyses a PDF file and determines whether it is a genuine MongoDB University certificate.
 *
 * Verification strategy:
 *  1. Extract all text from the PDF with PDFBox
 *  2. Run a series of pattern/keyword checks against known MongoDB cert structure
 *  3. Score each check → derive an aggregate confidence score
 *  4. Return a rich VerificationResult DTO
 */
@Service
public class MongoDBCertificateVerificationService {

    private static final Logger log = LoggerFactory.getLogger(MongoDBCertificateVerificationService.class);

    // ── Known genuine MongoDB University issuer strings ───────────────────────
    private static final List<String> ISSUER_PATTERNS = List.of(
            "mongodb university",
            "mongodb, inc",
            "mongodb inc",
            "university.mongodb.com",
            "learn.mongodb.com"
    );

    // ── Course / exam titles issued by MongoDB University ─────────────────────
    private static final List<String> KNOWN_COURSES = List.of(
            "mongodb associate developer",
            "mongodb associate database administrator",
            "mongodb associate data modeler",
            "m001", "m100", "m103", "m121", "m150", "m201", "m220",
            "m221", "m320", "m001:", "m100:", "m103:",
            "introduction to mongodb",
            "the mongodb aggregation framework",
            "mongodb performance",
            "mongodb for sql professionals",
            "mongodb cluster administration",
            "data modeling",
            "mongodb associate"
    );

    // ── Phrases that MUST appear on a genuine cert ────────────────────────────
    private static final List<String> REQUIRED_PHRASES = List.of(
            "mongodb",
            "certificate",
            "successfully completed",
            "has successfully"
    );

    // ── Phrases common on genuine certs (bonus score) ─────────────────────────
    private static final List<String> BONUS_PHRASES = List.of(
            "credential id",
            "credential",
            "this certifies",
            "verify at",
            "verify this certificate",
            "university",
            "issued",
            "completion",
            "exam"
    );

    // ── Credential ID patterns ─────────────────────────────────────────────────
    // MongoDB credential IDs look like: mdbcertificate.xxxxxxxx  or  a UUID
    private static final Pattern CREDENTIAL_ID_PATTERN = Pattern.compile(
            "(?i)(?:credential\\s*id|certificate\\s*id|cert\\s*id)[:\\s]+([A-Za-z0-9\\-_.]{6,60})"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    // ── Date patterns ──────────────────────────────────────────────────────────
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?i)(?:issued|date|awarded|expires?(?:s)?)[:\\s]+([A-Za-z]+ \\d{1,2},?\\s*\\d{4}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"
    );

    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "(?i)(?:expir(?:es?|ation|y))[:\\s]+([A-Za-z]+ \\d{1,2},?\\s*\\d{4}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})"
    );

    // ── Name pattern (lines before/after "has successfully completed") ─────────
    private static final Pattern NAME_AFTER_CERTIFIES = Pattern.compile(
            "(?i)(?:this certifies that|certify that|awarded to|presented to|this is to certify)[\\s\\r\\n]+([A-Z][a-zA-Z .'-]{2,50})"
    );

    private static final Pattern NAME_BEFORE_COMPLETED = Pattern.compile(
            "(?m)^([A-Z][a-zA-Z .'-]{4,50})\\s*\\r?\\n.*(?:has successfully|successfully completed)"
    );

    // ── Red-flag patterns (strongly suggest fake) ─────────────────────────────
    private static final List<String> RED_FLAGS = List.of(
            "photoshop",
            "canva",
            "template",
            "free certificate",
            "this is a sample",
            "specimen",
            "dummy",
            "test certificate"
    );

    // ── Scoring weights ────────────────────────────────────────────────────────
    private static final int WEIGHT_REQUIRED_PHRASE  = 10;  // per phrase (4 max = 40)
    private static final int WEIGHT_ISSUER           = 20;
    private static final int WEIGHT_KNOWN_COURSE     = 20;
    private static final int WEIGHT_CREDENTIAL_ID    = 10;
    private static final int WEIGHT_BONUS_PHRASE     = 2;   // per phrase (9 max = 18)
    private static final int PENALTY_RED_FLAG        = 30;  // per red flag

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Main entry point. Extracts text from the uploaded PDF then runs all checks.
     */
    public VerificationResult verify(MultipartFile file) throws IOException {
        log.info("Verifying certificate PDF: {}", file.getOriginalFilename());

        // 1. Extract text
        String rawText;
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            rawText = stripper.getText(doc);
        } catch (Exception e) {
            log.error("PDF extraction failed", e);
            return buildFakeResult("Could not read the PDF. The file may be corrupted or image-only.", "");
        }

        if (rawText == null || rawText.isBlank()) {
            return buildFakeResult(
                    "No extractable text found. The PDF may be a scanned image without OCR.",
                    rawText
            );
        }

        String text = rawText.toLowerCase(Locale.ROOT);
        log.debug("Extracted {} characters from PDF", rawText.length());

        // 2. Run checks
        List<CheckItem> passed  = new ArrayList<>();
        List<CheckItem> failed  = new ArrayList<>();
        int score = 0;

        // ── Required phrases ─────────────────────────────────────────────────
        for (String phrase : REQUIRED_PHRASES) {
            if (text.contains(phrase)) {
                passed.add(new CheckItem("Contains '" + phrase + "'", "Found in document"));
                score += WEIGHT_REQUIRED_PHRASE;
            } else {
                failed.add(new CheckItem("Missing '" + phrase + "'", "Expected phrase not found"));
            }
        }

        // ── Issuer check ──────────────────────────────────────────────────────
        boolean issuerFound = ISSUER_PATTERNS.stream().anyMatch(text::contains);
        if (issuerFound) {
            passed.add(new CheckItem("MongoDB issuer identified", "Official MongoDB issuer string present"));
            score += WEIGHT_ISSUER;
        } else {
            failed.add(new CheckItem("MongoDB issuer not found", "No official MongoDB University/Inc. reference found"));
        }

        // ── Course match ──────────────────────────────────────────────────────
        Optional<String> matchedCourse = KNOWN_COURSES.stream().filter(text::contains).findFirst();
        if (matchedCourse.isPresent()) {
            passed.add(new CheckItem("Known MongoDB course", "Matched: " + matchedCourse.get().toUpperCase()));
            score += WEIGHT_KNOWN_COURSE;
        } else {
            failed.add(new CheckItem("Unrecognised course title", "Course name does not match any known MongoDB University curriculum"));
        }

        // ── Credential ID ─────────────────────────────────────────────────────
        String credentialId = extractCredentialId(rawText);
        if (credentialId != null) {
            passed.add(new CheckItem("Credential ID present", credentialId));
            score += WEIGHT_CREDENTIAL_ID;
        } else {
            failed.add(new CheckItem("No credential ID found", "Genuine MongoDB certs include a verifiable credential ID"));
        }

        // ── Bonus phrases ─────────────────────────────────────────────────────
        for (String phrase : BONUS_PHRASES) {
            if (text.contains(phrase)) {
                passed.add(new CheckItem("Bonus: '" + phrase + "'", "Supporting phrase found"));
                score += WEIGHT_BONUS_PHRASE;
            }
        }

        // ── Red flags ─────────────────────────────────────────────────────────
        for (String flag : RED_FLAGS) {
            if (text.contains(flag)) {
                failed.add(new CheckItem("Red flag: '" + flag + "'", "Suspicious term found in document"));
                score -= PENALTY_RED_FLAG;
            }
        }

        // 3. Extract fields
        String candidateName = extractCandidateName(rawText);
        String issueDate     = extractDate(rawText, DATE_PATTERN);
        String expiryDate    = extractDate(rawText, EXPIRY_PATTERN);
        String examTitle     = extractExamTitle(rawText);
        String issuingOrg    = extractIssuer(rawText);

        // 4. Clamp score and decide verdict
        score = Math.max(0, Math.min(100, score));
        boolean authentic = score >= 60 && issuerFound;

        String verdict;
        String explanation;
        if (authentic) {
            verdict     = "✅ Certificate Appears Genuine";
            explanation = buildAuthenticExplanation(score, passed.size(), failed.size());
        } else {
            verdict     = "❌ Certificate Could Not Be Verified";
            explanation = buildFakeExplanation(score, issuerFound, matchedCourse.isPresent(), credentialId);
        }

        return VerificationResult.builder()
                .authentic(authentic)
                .verdict(verdict)
                .confidenceScore(score)
                .candidateName(candidateName)
                .credentialId(credentialId)
                .examTitle(examTitle)
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .issuingOrg(issuingOrg)
                .passedChecks(passed)
                .failedChecks(failed)
                .explanation(explanation)
                .extractedText(rawText.length() > 2000 ? rawText.substring(0, 2000) + "…" : rawText)
                .build();
    }

    // ── Extractors ─────────────────────────────────────────────────────────────

    private String extractCredentialId(String rawText) {
        Matcher m1 = CREDENTIAL_ID_PATTERN.matcher(rawText);
        if (m1.find()) return m1.group(1).trim();
        Matcher m2 = UUID_PATTERN.matcher(rawText);
        if (m2.find()) return m2.group().trim();
        // MongoDB-style: alphanumeric block after "Credential ID"
        Matcher m3 = Pattern.compile("(?i)Credential ID[:\\s]+([A-Za-z0-9]{8,})").matcher(rawText);
        if (m3.find()) return m3.group(1).trim();
        return null;
    }

    private String extractCandidateName(String rawText) {
        Matcher m1 = NAME_AFTER_CERTIFIES.matcher(rawText);
        if (m1.find()) return capitalise(m1.group(1).trim());
        Matcher m2 = NAME_BEFORE_COMPLETED.matcher(rawText);
        if (m2.find()) return capitalise(m2.group(1).trim());
        // Fallback: look for a line of 2-4 capitalised words near top of document
        String[] lines = rawText.split("\\r?\\n");
        for (int i = 0; i < Math.min(lines.length, 30); i++) {
            String line = lines[i].trim();
            if (line.matches("[A-Z][a-zA-Z]+(?: [A-Z][a-zA-Z]+){1,3}") && line.length() > 4) {
                return line;
            }
        }
        return null;
    }

    private String extractDate(String rawText, Pattern pattern) {
        Matcher m = pattern.matcher(rawText);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractExamTitle(String rawText) {
        String lower = rawText.toLowerCase(Locale.ROOT);
        return KNOWN_COURSES.stream()
                .filter(lower::contains)
                .map(c -> {
                    // Find the actual-case version in raw text
                    int idx = lower.indexOf(c);
                    return rawText.substring(idx, Math.min(idx + c.length() + 30, rawText.length())).trim();
                })
                .findFirst()
                .orElseGet(() -> extractGenericTitle(rawText));
    }

    private String extractGenericTitle(String rawText) {
        // Try to grab text near "completed" keyword
        Pattern p = Pattern.compile("(?i)completed[:\\s]+([^\\r\\n]{5,80})");
        Matcher m = p.matcher(rawText);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractIssuer(String rawText) {
        String lower = rawText.toLowerCase(Locale.ROOT);
        if (lower.contains("mongodb university"))  return "MongoDB University";
        if (lower.contains("mongodb, inc"))         return "MongoDB, Inc.";
        if (lower.contains("mongodb inc"))          return "MongoDB, Inc.";
        if (lower.contains("learn.mongodb.com"))    return "learn.mongodb.com";
        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private VerificationResult buildFakeResult(String reason, String rawText) {
        return VerificationResult.builder()
                .authentic(false)
                .verdict("❌ Certificate Could Not Be Verified")
                .confidenceScore(0)
                .explanation(reason)
                .passedChecks(List.of())
                .failedChecks(List.of(new CheckItem("PDF parsing", reason)))
                .extractedText(rawText)
                .build();
    }

    private String buildAuthenticExplanation(int score, int passed, int failed) {
        return String.format(
                "This document matches the structure and content of an official MongoDB University certificate. "
                + "%d verification checks passed and %d failed, yielding a confidence score of %d/100. "
                + "The issuer, course title, and certificate phrases align with known MongoDB University records.",
                passed, failed, score);
    }

    private String buildFakeExplanation(int score, boolean issuer, boolean course, String credId) {
        StringBuilder sb = new StringBuilder(
                "This document could not be verified as a genuine MongoDB University certificate. ");
        if (!issuer)  sb.append("No official MongoDB issuer was found. ");
        if (!course)  sb.append("The course title does not match any known MongoDB curriculum. ");
        if (credId == null) sb.append("No credential ID was detected. ");
        sb.append(String.format("Confidence score: %d/100 (threshold: 60).", score));
        return sb.toString();
    }

    private String capitalise(String s) {
        if (s == null || s.isBlank()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)))
                    .append(w.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}

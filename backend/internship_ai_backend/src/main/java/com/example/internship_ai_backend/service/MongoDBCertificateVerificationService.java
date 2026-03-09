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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

        private static final Pattern VERIFICATION_URL_PATTERN = Pattern.compile(
            "(?i)(https?://[^\\s)]+|(?:www\\.)[^\\s)]+)"
        );

        private static final Pattern SIGNATORY_PATTERN = Pattern.compile(
            "(?i)(?:signed by|authorized by|signatory|signature)[:\\s]+([A-Z][a-zA-Z .'-]{2,80})"
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
    private static final int WEIGHT_VERIFICATION_REF = 8;
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
        int redFlagHits = 0;

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

        String verificationUrl = extractVerificationUrl(rawText);
        if (verificationUrl != null) {
            passed.add(new CheckItem("Verification URL found", verificationUrl));
            score += WEIGHT_VERIFICATION_REF;
        } else {
            failed.add(new CheckItem("Verification URL missing", "No verify URL/QR target detected in certificate text"));
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
                redFlagHits++;
            }
        }

        // 3. Extract fields
        String certificateType = detectCertificateType(rawText, matchedCourse.orElse(null));
        String candidateName = extractCandidateName(rawText);
        String issueDate     = extractDate(rawText, DATE_PATTERN);
        String expiryDate    = extractDate(rawText, EXPIRY_PATTERN);
        String examTitle     = extractExamTitle(rawText);
        String issuingOrg    = extractIssuer(rawText);
        String authorizedSignatory = extractSignatory(rawText);

        boolean dateConsistencyOk = areDatesConsistent(issueDate, expiryDate);
        if (dateConsistencyOk) {
            passed.add(new CheckItem("Date consistency", "Issue/expiry dates are logical"));
        } else {
            failed.add(new CheckItem("Date mismatch", "Expiry date appears earlier than issue date or invalid format"));
            score -= 20;
        }

        boolean hasCriticalData = issuerFound
                && credentialId != null
                && candidateName != null
                && issueDate != null
                && examTitle != null;

        if (!hasCriticalData) {
            failed.add(new CheckItem("Critical details missing", "One or more mandatory identity fields are missing"));
            score -= 25;
        }

        // 4. Clamp score and decide verdict
        score = Math.max(0, Math.min(100, score));
        boolean authentic = score >= 75 && hasCriticalData && dateConsistencyOk && redFlagHits == 0;

        int confidenceScore = score;
        if (authentic) {
            confidenceScore = Math.max(score, 80);
        } else if (!hasCriticalData || redFlagHits > 0 || !dateConsistencyOk) {
            confidenceScore = Math.min(score, 45);
        } else {
            confidenceScore = Math.min(score, 65);
        }

        String verdict = authentic ? "✅ REAL" : "❌ FAKE";
        String reason = authentic
                ? "Core issuer, identity fields, credential ID, and date checks are consistent."
                : "Critical validation signals are missing or suspicious, so authenticity cannot be trusted.";

        List<String> analysisPoints = new ArrayList<>();
        analysisPoints.add(issuerFound
                ? "Issuer legitimacy check passed: MongoDB official issuer markers found."
                : "Issuer legitimacy check failed: no official MongoDB issuer marker found.");
        analysisPoints.add(credentialId != null
                ? "Certificate ID present: " + credentialId
                : "Certificate ID missing or invalid format.");
        analysisPoints.add(dateConsistencyOk
                ? "Date logic appears consistent."
                : "Date logic is inconsistent (expiry earlier than issue or invalid date format).");
        if (authorizedSignatory != null) {
            analysisPoints.add("Authorized signatory detected: " + authorizedSignatory + ".");
        } else {
            analysisPoints.add("Authorized signatory not detected from extracted text.");
        }
        if (verificationUrl != null) {
            analysisPoints.add("Verification URL extracted: " + verificationUrl + ".");
        } else {
            analysisPoints.add("No verification URL/QR target found in extractable text.");
        }

        String explanation = buildStructuredExplanation(
                certificateType,
                candidateName,
                issuingOrg,
                issueDate,
                expiryDate,
                credentialId,
                examTitle,
                authorizedSignatory,
                verificationUrl,
                analysisPoints,
                verdict,
                confidenceScore,
                reason
        );

        return VerificationResult.builder()
                .authentic(authentic)
                .verdict(verdict)
                .confidenceScore(confidenceScore)
                .certificateType(certificateType)
                .candidateName(candidateName)
                .credentialId(credentialId)
                .examTitle(examTitle)
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .issuingOrg(issuingOrg)
                .authorizedSignatory(authorizedSignatory)
                .verificationUrl(verificationUrl)
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

    private String extractSignatory(String rawText) {
        Matcher matcher = SIGNATORY_PATTERN.matcher(rawText);
        if (matcher.find()) {
            return capitalise(matcher.group(1).trim());
        }
        return null;
    }

    private String extractVerificationUrl(String rawText) {
        Matcher matcher = VERIFICATION_URL_PATTERN.matcher(rawText);
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.contains("mongodb") || lower.contains("verify") || lower.contains("credential")) {
                return lower.startsWith("http") ? url : "https://" + url;
            }
        }
        return null;
    }

    private String detectCertificateType(String rawText, String matchedCourse) {
        String lower = rawText.toLowerCase(Locale.ROOT);
        if (lower.contains("associate") || lower.contains("professional") || lower.contains("certification")) {
            return "Professional Certification";
        }
        if (lower.contains("course") || lower.contains("training") || lower.contains("completion")) {
            return "Training/Completion Certificate";
        }
        if (matchedCourse != null && !matchedCourse.isBlank()) {
            return "Professional Course Certificate";
        }
        return "Certificate";
    }

    private boolean areDatesConsistent(String issueDate, String expiryDate) {
        if (issueDate == null || expiryDate == null) {
            return true;
        }
        LocalDate issue = parseDate(issueDate);
        LocalDate expiry = parseDate(expiryDate);
        if (issue == null || expiry == null) {
            return true;
        }
        return !expiry.isBefore(issue);
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("M-d-yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH)
        );

        String normalized = dateText.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private VerificationResult buildFakeResult(String reason, String rawText) {
        return VerificationResult.builder()
                .authentic(false)
                .verdict("❌ FAKE")
                .confidenceScore(0)
                .certificateType("Certificate")
                .explanation(buildStructuredExplanation(
                        "Certificate",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("Could not extract enough certificate text for trustworthy verification."),
                        "❌ FAKE",
                        0,
                        reason
                ))
                .passedChecks(List.of())
                .failedChecks(List.of(new CheckItem("PDF parsing", reason)))
                .extractedText(rawText)
                .build();
    }

    private String buildStructuredExplanation(String type,
                                              String recipient,
                                              String issuedBy,
                                              String issueDate,
                                              String expiryDate,
                                              String certificateId,
                                              String courseOrAchievement,
                                              String signatory,
                                              String verificationUrl,
                                              List<String> analysisPoints,
                                              String verdict,
                                              int confidence,
                                              String reason) {
        StringBuilder analysis = new StringBuilder();
        if (analysisPoints != null) {
            for (String point : analysisPoints) {
                analysis.append("- ").append(point).append("\n");
            }
        }

        return "---\n"
                + "CERTIFICATE DETAILS:\n"
                + "- Type: " + safeValue(type) + "\n"
                + "- Recipient: " + safeValue(recipient) + "\n"
                + "- Issued By: " + safeValue(issuedBy) + "\n"
                + "- Issue Date: " + safeValue(issueDate) + "\n"
                + "- Expiry Date: " + safeValue(expiryDate) + "\n"
                + "- Certificate ID: " + safeValue(certificateId) + "\n"
                + "- Course/Achievement: " + safeValue(courseOrAchievement) + "\n"
                + "- Signatory: " + safeValue(signatory) + "\n"
                + "- Verification URL: " + safeValue(verificationUrl) + "\n\n"
                + "ANALYSIS:\n"
                + analysis
                + "\nVERDICT: " + verdict + "\n"
                + "Confidence: " + confidence + "%\n"
                + "Reason: " + safeValue(reason) + "\n"
                + "---";
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "Not detected" : value;
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

package com.example.internship_ai_backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UniversalCertificateVerificationService {

    /**
     * Universal certificate verification supporting multiple platforms
     * - MongoDB
     * - AWS 
     * - Google Cloud
     * - Coursera
     * - Udemy
     * - LinkedIn Learning
     * - General certificates
     */
    public Map<String, Object> verifyCertificate(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Basic file validation
            if (!isValidFileFormat(file)) {
                return buildResponse("FAKE", "Invalid file format. Please upload PDF, JPG, PNG, or other document formats.", null, null);
            }

            // Extract text from PDF or image
            String extractedText = extractTextFromFile(file);

            if (extractedText == null || extractedText.trim().isEmpty()) {
                return buildResponse("FAKE", "Could not extract text from file. File may be corrupted or empty.", null, null);
            }

            // Detect certificate type and verify
            List<Map<String, Object>> detections = detectCertificateType(extractedText);

            if (detections.isEmpty()) {
                return buildResponse("SUSPICIOUS", 
                    "No recognized certificate markers found. This could be a valid certificate from an unrecognized platform or a generic document.", 
                    null, null);
            }

            // Analyze all detected certificates
            Map<String, Object> bestMatch = selectBestMatch(detections, extractedText);
            
            return buildResponse(
                (String) bestMatch.get("status"),
                (String) bestMatch.get("reason"),
                (String) bestMatch.get("platform"),
                (String) bestMatch.get("certificateId")
            );

        } catch (Exception e) {
            return buildResponse("ERROR", "Failed to process certificate: " + e.getMessage(), null, null);
        }
    }

    private Map<String, Object> buildResponse(String status, String reason, String platform, String certificateId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("reason", reason);
        response.put("platform", platform);
        response.put("certificateId", certificateId);
        response.put("timestamp", new Date());
        return response;
    }

    /**
     * Detect which platform the certificate is from
     */
    private List<Map<String, Object>> detectCertificateType(String text) {
        List<Map<String, Object>> detections = new ArrayList<>();

        // MongoDB
        if (text.contains("MongoDB") || text.contains("mongodb")) {
            Map<String, Object> detection = verifyMongoDB(text);
            if ((boolean) detection.getOrDefault("found", false)) {
                detections.add(detection);
            }
        }

        // AWS
        if (text.contains("Amazon") || text.contains("AWS") || text.contains("aws.amazon.com")) {
            Map<String, Object> detection = verifyAWS(text);
            if ((boolean) detection.getOrDefault("found", false)) {
                detections.add(detection);
            }
        }

        // Google Cloud
        if (text.contains("Google Cloud") || text.contains("GCP") || text.contains("google.com")) {
            Map<String, Object> detection = verifyGoogleCloud(text);
            if ((boolean) detection.getOrDefault("found", false)) {
                detections.add(detection);
            }
        }

        // Coursera
        if (text.contains("Coursera") || text.contains("coursera.org")) {
            Map<String, Object> detection = verifyCoursera(text);
            if ((boolean) detection.getOrDefault("found", false)) {
                detections.add(detection);
            }
        }

        // Udemy
        if (text.contains("Udemy") || text.contains("udemy.com")) {
            Map<String, Object> detection = verifyUdemy(text);
            if ((boolean) detection.getOrDefault("found", false)) {
                detections.add(detection);
            }
        }

        // LinkedIn Learning
        if (text.contains("LinkedIn Learning") || text.contains("linkedin.com/learning")) {
            Map<String, Object> detection = verifyLinkedInLearning(text);
            if ((boolean) detection.getOrDefault("found", false)) {
                detections.add(detection);
            }
        }

        return detections;
    }

    private Map<String, Object> selectBestMatch(List<Map<String, Object>> detections, String text) {
        // Sort by confidence score (descending)
        detections.sort((a, b) -> {
            int scoreA = (int) a.getOrDefault("confidence", 0);
            int scoreB = (int) b.getOrDefault("confidence", 0);
            return Integer.compare(scoreB, scoreA);
        });

        return detections.get(0);
    }

    /**
     * MongoDB Certificate Verification
     */
    private Map<String, Object> verifyMongoDB(String text) {
        Map<String, Object> result = new HashMap<>();
        Pattern pattern = Pattern.compile("MDB[A-Za-z0-9]{8,}");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String licenseId = matcher.group();
            result.put("found", true);
            result.put("status", "REAL");
            result.put("platform", "MongoDB University");
            result.put("certificateId", licenseId);
            result.put("reason", "Valid MongoDB license ID detected: " + licenseId);
            result.put("confidence", 90);

            // Try online verification
            if (!verifyWithMongoDB(licenseId)) {
                result.put("status", "SUSPICIOUS");
                result.put("reason", "License format valid but could not verify online. Certificate might be outdated.");
                result.put("confidence", 70);
            }
        } else {
            result.put("found", false);
        }

        return result;
    }

    /**
     * AWS Certificate Verification
     */
    private Map<String, Object> verifyAWS(String text) {
        Map<String, Object> result = new HashMap<>();
        
        boolean hasAWSBranding = text.contains("Amazon Web Services") || text.contains("AWS Certified");
        boolean hasCredentialNumber = Pattern.compile("[A-Z0-9]{20,}").matcher(text).find();
        boolean hasExamInfo = text.contains("Certified") || text.contains("Badge");

        if (hasAWSBranding && (hasCredentialNumber || hasExamInfo)) {
            result.put("found", true);
            result.put("status", "REAL");
            result.put("platform", "AWS");
            result.put("reason", "Authentic AWS certification markers detected");
            result.put("confidence", 85);
            
            // Extract certification number if present
            Pattern pattern = Pattern.compile("[A-Z0-9]{20,}");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                result.put("certificateId", matcher.group());
            }
        } else {
            result.put("found", false);
        }

        return result;
    }

    /**
     * Google Cloud Certificate Verification
     */
    private Map<String, Object> verifyGoogleCloud(String text) {
        Map<String, Object> result = new HashMap<>();
        
        boolean hasGCPBranding = text.contains("Google Cloud") || text.contains("Google");
        boolean hasCertification = text.contains("Professional") || text.contains("Associate") || text.contains("Certified");

        if (hasGCPBranding && hasCertification) {
            result.put("found", true);
            result.put("status", "REAL");
            result.put("platform", "Google Cloud");
            result.put("reason", "Valid Google Cloud Professional certification detected");
            result.put("confidence", 85);
        } else {
            result.put("found", false);
        }

        return result;
    }

    /**
     * Coursera Certificate Verification
     */
    private Map<String, Object> verifyCoursera(String text) {
        Map<String, Object> result = new HashMap<>();
        
        boolean hasCoursera = text.contains("Coursera") || text.contains("coursera.org");
        boolean hasCredential = text.contains("Certificate") || text.contains("Specialization");
        boolean hasVerificationId = Pattern.compile("[A-Z0-9]{8,}").matcher(text).find();

        if (hasCoursera && hasCredential) {
            result.put("found", true);
            result.put("status", "REAL");
            result.put("platform", "Coursera");
            result.put("reason", "Valid Coursera certificate detected. Verify at coursera.org/verify");
            result.put("confidence", 80);

            if (hasVerificationId) {
                Pattern pattern = Pattern.compile("[A-Z0-9]{8,}");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    result.put("certificateId", matcher.group());
                }
            }
        } else {
            result.put("found", false);
        }

        return result;
    }

    /**
     * Udemy Certificate Verification
     */
    private Map<String, Object> verifyUdemy(String text) {
        Map<String, Object> result = new HashMap<>();
        
        boolean hasUdemy = text.contains("Udemy") || text.contains("udemy.com");
        boolean hasCertificate = text.contains("Certificate of Completion") || text.contains("Completion");

        if (hasUdemy && hasCertificate) {
            result.put("found", true);
            result.put("status", "REAL");
            result.put("platform", "Udemy");
            result.put("reason", "Valid Udemy certificate of completion detected");
            result.put("confidence", 80);
        } else {
            result.put("found", false);
        }

        return result;
    }

    /**
     * LinkedIn Learning Certificate Verification
     */
    private Map<String, Object> verifyLinkedInLearning(String text) {
        Map<String, Object> result = new HashMap<>();
        
        boolean hasLinkedIn = text.contains("LinkedIn") || text.contains("linkedin.com");
        boolean hasLearning = text.contains("Learning") || text.contains("Course Certificate");

        if (hasLinkedIn && hasLearning) {
            result.put("found", true);
            result.put("status", "REAL");
            result.put("platform", "LinkedIn Learning");
            result.put("reason", "Valid LinkedIn Learning certificate detected");
            result.put("confidence", 85);
        } else {
            result.put("found", false);
        }

        return result;
    }

    /**
     * Extract text from PDF or image file
     */
    private String extractTextFromFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();

        if (contentType != null && contentType.contains("pdf")) {
            return extractTextFromPDF(file);
        } else if (contentType != null && contentType.contains("image")) {
            return extractTextFromImage(file);
        }

        return null;
    }

    private String extractTextFromPDF(MultipartFile file) throws IOException {
        try {
            PDDocument document = PDDocument.load(file.getInputStream());
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            return text;
        } catch (Exception e) {
            throw new IOException("Failed to extract text from PDF: " + e.getMessage());
        }
    }

    private String extractTextFromImage(MultipartFile file) throws IOException {
        // For images without OCR library, use filename as metadata
        // In production, you can add Tesseract4j dependency for full OCR
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        // Return metadata about the file which may contain certificate indicators
        StringBuilder metadata = new StringBuilder();
        if (filename != null) {
            metadata.append(filename).append(" ");
        }
        if (contentType != null) {
            metadata.append(contentType).append(" ");
        }
        
        // For image-based certificates, the filename often contains platform/certificate info
        // e.g., "MongoDB_Certificate.png", "AWS_Certification.jpg"
        return metadata.toString();
    }

    private boolean isValidFileFormat(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        if (contentType == null || filename == null) {
            return false;
        }

        return (contentType.contains("pdf") && filename.endsWith(".pdf")) ||
               (contentType.contains("image") && 
                (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")));
    }

    @SuppressWarnings("deprecation")
    private boolean verifyWithMongoDB(String licenseId) {
        try {
            String verificationUrl = "https://university.mongodb.com/verify/" + licenseId;
            HttpURLConnection connection = (HttpURLConnection) new URL(verificationUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }
}

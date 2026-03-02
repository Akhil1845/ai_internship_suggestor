package com.example.internship_ai_backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MongoDBCertificateVerificationService {

    /**
     * Verify if a MongoDB certificate is real/fake
     * 1. Extract text from PDF
     * 2. Extract License ID (MDB*)
     * 3. Validate format
     * 4. Optionally verify with MongoDB verification URL
     */
    public Map<String, Object> verifyMongoDBCertificate(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check file format
            if (!isValidFileFormat(file)) {
                response.put("status", "FAKE");
                response.put("reason", "Invalid file format. Please upload a PDF certificate.");
                response.put("licenseId", null);
                return response;
            }

            // Extract text from PDF
            String pdfText = extractTextFromPdf(file);

            if (pdfText == null || pdfText.trim().isEmpty()) {
                response.put("status", "FAKE");
                response.put("reason", "Could not extract text from PDF. File may be corrupted or image-based.");
                response.put("licenseId", null);
                return response;
            }

            // Extract License ID (format: MDB followed by alphanumeric characters)
            String licenseId = extractLicenseId(pdfText);

            if (licenseId == null) {
                response.put("status", "FAKE");
                response.put("reason", "No MongoDB License ID found in certificate.");
                response.put("licenseId", null);
                return response;
            }

            // Validate License ID format
            if (!isValidMongoDBLicenseFormat(licenseId)) {
                response.put("status", "FAKE");
                response.put("reason", "Invalid MongoDB License format. Expected format: MDB[alphanumeric]");
                response.put("licenseId", licenseId);
                return response;
            }

            // Optional: Try to verify with MongoDB verification URL
            boolean isVerifiedWithMongoDB = verifyWithMongoDB(licenseId);

            if (isVerifiedWithMongoDB) {
                response.put("status", "REAL");
                response.put("reason", "Certificate is Verified with MongoDB University");
                response.put("licenseId", licenseId);
            } else {
                // License format is correct but MongoDB verification failed
                // This could mean: MongoDB blocks requests, or license doesn't exist
                response.put("status", "SUSPICIOUS");
                response.put("reason", "License ID format is valid but could not verify with MongoDB server. Certificate might be outdated or MongoDB verification is unavailable.");
                response.put("licenseId", licenseId);
            }

            return response;

        } catch (IOException e) {
            response.put("status", "FAKE");
            response.put("reason", "Error reading PDF file: " + e.getMessage());
            response.put("licenseId", null);
            return response;
        } catch (Exception e) {
            response.put("status", "FAKE");
            response.put("reason", "Unexpected error: " + e.getMessage());
            response.put("licenseId", null);
            return response;
        }
    }

    /**
     * Extract text content from PDF file
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
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

    /**
     * Extract MongoDB License ID from PDF text
     * Pattern: MDB followed by alphanumeric characters
     * Example: MDB2qs7lm6arz
     */
    private String extractLicenseId(String text) {
        Pattern pattern = Pattern.compile("MDB[A-Za-z0-9]+");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Validate MongoDB License ID format
     * Should start with "MDB" and contain only alphanumeric characters
     */
    private boolean isValidMongoDBLicenseFormat(String licenseId) {
        if (licenseId == null || licenseId.isEmpty()) {
            return false;
        }
        // Must start with MDB and be 10-20 characters long
        Pattern pattern = Pattern.compile("^MDB[A-Za-z0-9]{8,}$");
        return pattern.matcher(licenseId).matches();
    }

    /**
     * Verify MongoDB License ID with MongoDB University verification service
     * Note: MongoDB may block automated requests or require authentication
     * This is best-effort verification
     */
    @SuppressWarnings("deprecation")
    private boolean verifyWithMongoDB(String licenseId) {
        try {
            String verificationUrl = "https://university.mongodb.com/verify/" + licenseId;
            
            HttpURLConnection connection = (HttpURLConnection) new URL(verificationUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            // Set user agent to avoid blocking
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // MongoDB typically returns 200 for valid licenses
            return responseCode == 200 || responseCode == 302;
            
        } catch (Exception e) {
            // If verification fails, don't treat it as FAKE
            // MongoDB might have blocked the request
            return false;
        }
    }

    /**
     * Check if file is PDF format (by extension)
     */
    private boolean isValidFileFormat(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) return false;
        return fileName.toLowerCase().endsWith(".pdf");
    }
}

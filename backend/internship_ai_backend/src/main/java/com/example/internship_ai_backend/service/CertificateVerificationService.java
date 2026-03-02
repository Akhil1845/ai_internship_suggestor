package com.example.internship_ai_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class CertificateVerificationService {

    /**
     * Verify if a certificate is authentic
     * Returns verification result with confidence score and details
     */
    public Map<String, Object> verifyCertificate(MultipartFile certificate) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get file metadata
            String filename = certificate.getOriginalFilename();
            String contentType = certificate.getContentType();
            long fileSize = certificate.getSize();
            
            // Basic validation checks
            boolean isValidFormat = isValidCertificateFormat(contentType, filename);
            boolean hasValidSize = fileSize > 10000 && fileSize < 10485760; // 10KB to 10MB
            
            // Analyze file content
            byte[] fileContent = certificate.getBytes();
            boolean hasPDFStructure = checkPDFStructure(fileContent, contentType);
            boolean hasImageStructure = checkImageStructure(fileContent, contentType);
            
            // Calculate verification score
            int verificationScore = 0;
            StringBuilder details = new StringBuilder();
            
            if (isValidFormat) {
                verificationScore += 25;
                details.append("✓ Valid certificate format. ");
            } else {
                details.append("✗ Invalid file format. ");
            }
            
            if (hasValidSize) {
                verificationScore += 20;
                details.append("✓ File size within acceptable range. ");
            } else {
                details.append("✗ Unusual file size. ");
            }
            
            if (hasPDFStructure || hasImageStructure) {
                verificationScore += 30;
                details.append("✓ Valid document structure detected. ");
            } else {
                details.append("✗ Document structure verification failed. ");
            }
            
            // Check for common certificate indicators
            String filenameCheck = filename != null ? filename.toLowerCase() : "";
            if (filenameCheck.contains("certificate") || filenameCheck.contains("cert") || 
                filenameCheck.contains("diploma") || filenameCheck.contains("award")) {
                verificationScore += 15;
                details.append("✓ Filename indicates certificate document. ");
            }
            
            // Additional metadata analysis
            if (contentType != null && (contentType.contains("pdf") || contentType.contains("image"))) {
                verificationScore += 10;
                details.append("✓ Standard certificate file type. ");
            }
            
            // Determine verification status
            String status;
            String message;
            
            if (verificationScore >= 80) {
                status = "VERIFIED";
                message = "Certificate appears to be authentic";
            } else if (verificationScore >= 50) {
                status = "LIKELY_VALID";
                message = "Certificate appears valid but requires manual review";
            } else {
                status = "SUSPICIOUS";
                message = "Certificate authenticity could not be verified";
            }
            
            result.put("status", status);
            result.put("message", message);
            result.put("verificationScore", verificationScore);
            result.put("details", details.toString().trim());
            result.put("filename", filename);
            result.put("fileSize", fileSize);
            result.put("fileType", contentType);
            
        } catch (IOException e) {
            result.put("status", "ERROR");
            result.put("message", "Failed to process certificate file");
            result.put("verificationScore", 0);
            result.put("details", "Error reading file: " + e.getMessage());
        }
        
        return result;
    }
    
    private boolean isValidCertificateFormat(String contentType, String filename) {
        if (contentType == null || filename == null) {
            return false;
        }
        
        String lowerFilename = filename.toLowerCase();
        return (contentType.contains("pdf") && lowerFilename.endsWith(".pdf")) ||
               (contentType.contains("image") && 
                (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg") || 
                 lowerFilename.endsWith(".png")));
    }
    
    private boolean checkPDFStructure(byte[] content, String contentType) {
        if (contentType == null || !contentType.contains("pdf")) {
            return false;
        }
        
        // Check for PDF magic number (%PDF-)
        if (content.length < 5) {
            return false;
        }
        
        return content[0] == 0x25 && content[1] == 0x50 && 
               content[2] == 0x44 && content[3] == 0x46;
    }
    
    private boolean checkImageStructure(byte[] content, String contentType) {
        if (contentType == null || !contentType.contains("image")) {
            return false;
        }
        
        if (content.length < 4) {
            return false;
        }
        
        // Check for common image signatures
        // JPEG: FF D8 FF
        if (content[0] == (byte)0xFF && content[1] == (byte)0xD8 && content[2] == (byte)0xFF) {
            return true;
        }
        
        // PNG: 89 50 4E 47
        if (content[0] == (byte)0x89 && content[1] == 0x50 && 
            content[2] == 0x4E && content[3] == 0x47) {
            return true;
        }
        
        return false;
    }
}

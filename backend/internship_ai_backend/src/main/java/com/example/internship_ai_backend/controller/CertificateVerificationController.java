package com.example.internship_ai_backend.controller;

import com.example.internship_ai_backend.dto.VerificationResult;
import com.example.internship_ai_backend.service.MongoDBCertificateVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * REST Controller for Certificate PDF Verification.
 * Provides endpoints for verifying MongoDB and other certificates.
 *
 * POST /api/verify/certificate   — upload PDF, get verification result
 * GET  /api/verify/health        — health check
 */
@RestController
@RequestMapping("/api/verify")
@CrossOrigin(origins = "*")
public class CertificateVerificationController {

    private static final Logger log = LoggerFactory.getLogger(CertificateVerificationController.class);
    
    private final MongoDBCertificateVerificationService verifierService;
    
    public CertificateVerificationController(MongoDBCertificateVerificationService verifierService) {
        this.verifierService = verifierService;
    }

    /**
     * Accepts a PDF file upload and returns a structured verification result.
     *
     * POST /api/verify/certificate
     * Content-Type: multipart/form-data
     * Field name: file
     */
    @PostMapping(value = "/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResult> verifyCertificate(
            @RequestParam("file") MultipartFile file) {

        // Validate file presence
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    VerificationResult.builder()
                            .authentic(false)
                            .verdict("❌ No file uploaded")
                            .confidenceScore(0)
                            .explanation("Please upload a PDF file to verify.")
                            .build()
            );
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            // Also allow by filename extension as a fallback
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            if (!name.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(
                        VerificationResult.builder()
                                .authentic(false)
                                .verdict("❌ Invalid file type")
                                .confidenceScore(0)
                                .explanation("Only PDF files are accepted.")
                                .build()
                );
            }
        }

        log.info("Received certificate verification request: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            VerificationResult result = verifierService.verify(file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Error processing PDF", e);
            return ResponseEntity.internalServerError().body(
                    VerificationResult.builder()
                            .authentic(false)
                            .verdict("❌ Processing Error")
                            .confidenceScore(0)
                            .explanation("An error occurred while processing the PDF: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Simple health-check endpoint.
     * GET /api/verify/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Certificate Verifier",
                "version", "1.0"
        ));
    }
}

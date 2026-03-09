package com.example.internship_ai_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response returned after analysing a PDF certificate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationResult {

    /** True = real MongoDB certificate, False = fake / unrecognised */
    private boolean authentic;

    /** Short headline for the UI */
    private String verdict;

    /** Confidence score 0–100 */
    private int confidenceScore;

    /** Certificate title/type (academic/professional/training) */
    private String certificateType;

    /** Extracted candidate name */
    private String candidateName;

    /** Extracted certificate / credential ID */
    private String credentialId;

    /** Extracted exam / course title */
    private String examTitle;

    /** Extracted issue date */
    private String issueDate;

    /** Extracted expiry date (if any) */
    private String expiryDate;

    /** Issuing organisation found in document */
    private String issuingOrg;

    /** Authorized signatory found in document */
    private String authorizedSignatory;

    /** Verification URL/QR target found in document */
    private String verificationUrl;

    /** Which checks passed */
    private List<CheckItem> passedChecks;

    /** Which checks failed */
    private List<CheckItem> failedChecks;

    /** Human-readable explanation */
    private String explanation;

    /** Raw extracted text (for debugging, omit in prod if desired) */
    private String extractedText;

    // Constructors
    public VerificationResult() {}

    public VerificationResult(boolean authentic, String verdict, int confidenceScore, String certificateType,
                             String candidateName, String credentialId, String examTitle, String issueDate, String expiryDate,
                             String issuingOrg, String authorizedSignatory, String verificationUrl,
                             List<CheckItem> passedChecks, List<CheckItem> failedChecks,
                             String explanation, String extractedText) {
        this.authentic = authentic;
        this.verdict = verdict;
        this.confidenceScore = confidenceScore;
        this.certificateType = certificateType;
        this.candidateName = candidateName;
        this.credentialId = credentialId;
        this.examTitle = examTitle;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.issuingOrg = issuingOrg;
        this.authorizedSignatory = authorizedSignatory;
        this.verificationUrl = verificationUrl;
        this.passedChecks = passedChecks;
        this.failedChecks = failedChecks;
        this.explanation = explanation;
        this.extractedText = extractedText;
    }

    // Getters and Setters
    public boolean isAuthentic() { return authentic; }
    public void setAuthentic(boolean authentic) { this.authentic = authentic; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getCertificateType() { return certificateType; }
    public void setCertificateType(String certificateType) { this.certificateType = certificateType; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getIssuingOrg() { return issuingOrg; }
    public void setIssuingOrg(String issuingOrg) { this.issuingOrg = issuingOrg; }

    public String getAuthorizedSignatory() { return authorizedSignatory; }
    public void setAuthorizedSignatory(String authorizedSignatory) { this.authorizedSignatory = authorizedSignatory; }

    public String getVerificationUrl() { return verificationUrl; }
    public void setVerificationUrl(String verificationUrl) { this.verificationUrl = verificationUrl; }

    public List<CheckItem> getPassedChecks() { return passedChecks; }
    public void setPassedChecks(List<CheckItem> passedChecks) { this.passedChecks = passedChecks; }

    public List<CheckItem> getFailedChecks() { return failedChecks; }
    public void setFailedChecks(List<CheckItem> failedChecks) { this.failedChecks = failedChecks; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean authentic;
        private String verdict;
        private int confidenceScore;
        private String certificateType;
        private String candidateName;
        private String credentialId;
        private String examTitle;
        private String issueDate;
        private String expiryDate;
        private String issuingOrg;
        private String authorizedSignatory;
        private String verificationUrl;
        private List<CheckItem> passedChecks;
        private List<CheckItem> failedChecks;
        private String explanation;
        private String extractedText;

        public Builder authentic(boolean authentic) { this.authentic = authentic; return this; }
        public Builder verdict(String verdict) { this.verdict = verdict; return this; }
        public Builder confidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; return this; }
        public Builder certificateType(String certificateType) { this.certificateType = certificateType; return this; }
        public Builder candidateName(String candidateName) { this.candidateName = candidateName; return this; }
        public Builder credentialId(String credentialId) { this.credentialId = credentialId; return this; }
        public Builder examTitle(String examTitle) { this.examTitle = examTitle; return this; }
        public Builder issueDate(String issueDate) { this.issueDate = issueDate; return this; }
        public Builder expiryDate(String expiryDate) { this.expiryDate = expiryDate; return this; }
        public Builder issuingOrg(String issuingOrg) { this.issuingOrg = issuingOrg; return this; }
        public Builder authorizedSignatory(String authorizedSignatory) { this.authorizedSignatory = authorizedSignatory; return this; }
        public Builder verificationUrl(String verificationUrl) { this.verificationUrl = verificationUrl; return this; }
        public Builder passedChecks(List<CheckItem> passedChecks) { this.passedChecks = passedChecks; return this; }
        public Builder failedChecks(List<CheckItem> failedChecks) { this.failedChecks = failedChecks; return this; }
        public Builder explanation(String explanation) { this.explanation = explanation; return this; }
        public Builder extractedText(String extractedText) { this.extractedText = extractedText; return this; }

        public VerificationResult build() {
            return new VerificationResult(authentic, verdict, confidenceScore, certificateType, candidateName,
                    credentialId, examTitle, issueDate, expiryDate, issuingOrg, authorizedSignatory, verificationUrl,
                    passedChecks, failedChecks, explanation, extractedText);
        }
    }

    public static class CheckItem {
        private String label;
        private String detail;

        public CheckItem() {}

        public CheckItem(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
    }
}

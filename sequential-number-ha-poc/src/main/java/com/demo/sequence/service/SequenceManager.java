package com.demo.sequence.service;

import com.demo.sequence.model.AuditRecord;
import com.demo.sequence.model.SequenceRequest;
import com.demo.sequence.model.SequenceResponse;
import com.demo.sequence.model.SequenceStats;

import java.util.List;

/**
 * Core Interface for Sequence Number Management
 * 
 * Defines the contract for generating, releasing, and managing
 * sequence numbers across different implementations (embedded, distributed).
 */
public interface SequenceManager {

    /**
     * Generate the next sequence number(s) for a request
     * 
     * @param request The sequence generation request
     * @return Response containing generated sequence numbers
     */
    SequenceResponse getNextSequence(SequenceRequest request);

    /**
     * Release a sequence number (creates a gap for recovery)
     * 
     * @param sequenceNumber The sequence number to release
     * @param siteId Site that released the sequence
     * @param partitionId Partition that released the sequence
     * @param reason Reason for release (e.g., "suppressed-bill", "cancelled-invoice")
     * @return true if successfully released
     */
    boolean releaseSequence(long sequenceNumber, String siteId, String partitionId, String reason);

    /**
     * Get current system statistics
     * 
     * @return Current sequence generation statistics
     */
    SequenceStats getStats();

    /**
     * Get audit trail of sequence operations
     * 
     * @param limit Maximum number of records to return
     * @return List of audit records
     */
    List<AuditRecord> getAuditTrail(int limit);

    /**
     * Get audit records for a specific sequence number
     * 
     * @param sequenceNumber The sequence number to query
     * @return List of audit records for the sequence
     */
    List<AuditRecord> getAuditTrailForSequence(long sequenceNumber);

    /**
     * Check if system is healthy
     * 
     * @return true if system is operating normally
     */
    boolean isHealthy();

    /**
     * Validate sequence number integrity
     * Checks for gaps, duplicates, and consistency
     * 
     * @return Validation results
     */
    ValidationResult validateSequenceIntegrity();

    /**
     * Reset the system (for demo purposes only)
     * Clears all sequences and starts fresh
     * 
     * @return true if successfully reset
     */
    boolean resetSystem();

    /**
     * Get available gaps that can be recovered
     * 
     * @return List of available sequence numbers for recovery
     */
    List<Long> getAvailableGaps();

    /**
     * Force recovery of a specific gap
     * 
     * @param sequenceNumber The gap to recover
     * @return true if successfully recovered
     */
    boolean recoverGap(long sequenceNumber);

    /**
     * Validation Result for sequence integrity checks
     */
    class ValidationResult {
        private boolean isValid;
        private List<Long> gaps;
        private List<Long> duplicates;
        private String summary;
        private List<String> issues;

        public ValidationResult(boolean isValid) {
            this.isValid = isValid;
        }

        // Getters and setters
        public boolean isValid() { return isValid; }
        public void setValid(boolean valid) { isValid = valid; }

        public List<Long> getGaps() { return gaps; }
        public void setGaps(List<Long> gaps) { this.gaps = gaps; }

        public List<Long> getDuplicates() { return duplicates; }
        public void setDuplicates(List<Long> duplicates) { this.duplicates = duplicates; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }

        @Override
        public String toString() {
            return "ValidationResult{" +
                    "isValid=" + isValid +
                    ", gaps=" + (gaps != null ? gaps.size() : 0) +
                    ", duplicates=" + (duplicates != null ? duplicates.size() : 0) +
                    ", summary='" + summary + '\'' +
                    '}';
        }
    }
}
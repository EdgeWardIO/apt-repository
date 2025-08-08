package com.demo.sequence.controller;

import com.demo.sequence.model.AuditRecord;
import com.demo.sequence.model.SequenceRequest;
import com.demo.sequence.model.SequenceResponse;
import com.demo.sequence.model.SequenceStats;
import com.demo.sequence.service.SequenceManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Sequence Number Generation
 * 
 * Provides HTTP endpoints for sequence generation, release, statistics,
 * and audit trail access.
 */
@RestController
@RequestMapping("/api/v1/sequence")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
public class SequenceController {

    private static final Logger logger = LoggerFactory.getLogger(SequenceController.class);

    @Autowired
    private SequenceManager sequenceManager;

    /**
     * Generate next sequence number(s) - POST version with request body
     */
    @PostMapping("/next")
    public ResponseEntity<SequenceResponse> getNextSequence(@Valid @RequestBody SequenceRequest request) {
        logger.info("Received sequence generation request: {}", request);
        
        try {
            SequenceResponse response = sequenceManager.getNextSequence(request);
            
            if (response.isSuccess()) {
                logger.info("Successfully generated {} sequence numbers for request {}", 
                          response.getSequenceNumbers().size(), request.getRequestId());
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to generate sequence for request {}: {}", 
                           request.getRequestId(), response.getError());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error processing sequence request: {}", request, e);
            return ResponseEntity.internalServerError()
                    .body(SequenceResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Generate next sequence number - GET version for simple browser testing
     */
    @GetMapping("/next")
    public ResponseEntity<SequenceResponse> getNextSequenceSimple(
            @RequestParam @NotBlank String siteId,
            @RequestParam @NotBlank String partitionId,
            @RequestParam @NotBlank String invoiceType,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100) Integer count) {
        
        logger.info("Simple sequence request: site={}, partition={}, type={}, count={}", 
                   siteId, partitionId, invoiceType, count);
        
        SequenceRequest request = new SequenceRequest(siteId, partitionId, invoiceType, count);
        return getNextSequence(request);
    }

    /**
     * Release a sequence number (creates a gap)
     */
    @PostMapping("/release")
    public ResponseEntity<Map<String, Object>> releaseSequence(@Valid @RequestBody ReleaseRequest releaseRequest) {
        logger.info("Received sequence release request: {}", releaseRequest);
        
        try {
            boolean success = sequenceManager.releaseSequence(
                releaseRequest.getSequenceNumber(),
                releaseRequest.getSiteId(),
                releaseRequest.getPartitionId(),
                releaseRequest.getReason()
            );
            
            Map<String, Object> response = Map.of(
                "success", success,
                "sequenceNumber", releaseRequest.getSequenceNumber(),
                "message", success ? "Sequence released successfully" : "Failed to release sequence",
                "timestamp", java.time.LocalDateTime.now()
            );
            
            if (success) {
                logger.info("Successfully released sequence number: {}", releaseRequest.getSequenceNumber());
                return ResponseEntity.ok(response);
            } else {
                logger.error("Failed to release sequence number: {}", releaseRequest.getSequenceNumber());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error releasing sequence: {}", releaseRequest, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false,
                        "error", "Internal server error: " + e.getMessage(),
                        "timestamp", java.time.LocalDateTime.now()
                    ));
        }
    }

    /**
     * Get current system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<SequenceStats> getStats() {
        logger.debug("Fetching sequence statistics");
        
        try {
            SequenceStats stats = sequenceManager.getStats();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error fetching statistics", e);
            return ResponseEntity.internalServerError()
                    .body(SequenceStats.empty());
        }
    }

    /**
     * Get audit trail of sequence operations
     */
    @GetMapping("/audit")
    public ResponseEntity<List<AuditRecord>> getAuditTrail(
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        
        logger.debug("Fetching audit trail with limit: {}", limit);
        
        try {
            List<AuditRecord> auditTrail = sequenceManager.getAuditTrail(limit);
            return ResponseEntity.ok(auditTrail);
            
        } catch (Exception e) {
            logger.error("Error fetching audit trail", e);
            return ResponseEntity.internalServerError().body(List.of());
        }
    }

    /**
     * Get audit trail for a specific sequence number
     */
    @GetMapping("/audit/{sequenceNumber}")
    public ResponseEntity<List<AuditRecord>> getAuditTrailForSequence(
            @PathVariable @NotNull long sequenceNumber) {
        
        logger.debug("Fetching audit trail for sequence number: {}", sequenceNumber);
        
        try {
            List<AuditRecord> auditTrail = sequenceManager.getAuditTrailForSequence(sequenceNumber);
            return ResponseEntity.ok(auditTrail);
            
        } catch (Exception e) {
            logger.error("Error fetching audit trail for sequence: {}", sequenceNumber, e);
            return ResponseEntity.internalServerError().body(List.of());
        }
    }

    /**
     * System health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        logger.debug("Health check requested");
        
        try {
            boolean isHealthy = sequenceManager.isHealthy();
            SequenceStats stats = sequenceManager.getStats();
            
            Map<String, Object> health = Map.of(
                "status", isHealthy ? "UP" : "DOWN",
                "healthy", isHealthy,
                "currentCounter", stats.getCurrentCounter(),
                "totalGenerated", stats.getTotalGenerated(),
                "availableGaps", stats.getAvailableGaps(),
                "systemHealthy", stats.isSystemHealthy(),
                "clusterHealthy", stats.isClusterHealthy(),
                "timestamp", java.time.LocalDateTime.now()
            );
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            logger.error("Error during health check", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "status", "DOWN",
                        "healthy", false,
                        "error", e.getMessage(),
                        "timestamp", java.time.LocalDateTime.now()
                    ));
        }
    }

    /**
     * Validate sequence integrity
     */
    @GetMapping("/validate")
    public ResponseEntity<SequenceManager.ValidationResult> validateSequenceIntegrity() {
        logger.info("Sequence integrity validation requested");
        
        try {
            SequenceManager.ValidationResult result = sequenceManager.validateSequenceIntegrity();
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error during sequence validation", e);
            SequenceManager.ValidationResult errorResult = new SequenceManager.ValidationResult(false);
            errorResult.setSummary("Validation failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Get available gaps
     */
    @GetMapping("/gaps")
    public ResponseEntity<Map<String, Object>> getAvailableGaps() {
        logger.debug("Fetching available gaps");
        
        try {
            List<Long> gaps = sequenceManager.getAvailableGaps();
            
            Map<String, Object> response = Map.of(
                "gaps", gaps,
                "count", gaps.size(),
                "nextGap", gaps.isEmpty() ? -1 : gaps.get(0),
                "timestamp", java.time.LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error fetching gaps", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", "Failed to fetch gaps: " + e.getMessage(),
                        "timestamp", java.time.LocalDateTime.now()
                    ));
        }
    }

    /**
     * Reset system (for demo purposes only)
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSystem() {
        logger.warn("System reset requested - this will clear all sequence data!");
        
        try {
            boolean success = sequenceManager.resetSystem();
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "System reset successfully" : "Failed to reset system",
                "timestamp", java.time.LocalDateTime.now()
            );
            
            if (success) {
                logger.info("System reset completed successfully");
                return ResponseEntity.ok(response);
            } else {
                logger.error("System reset failed");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error during system reset", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "success", false,
                        "error", "Reset failed: " + e.getMessage(),
                        "timestamp", java.time.LocalDateTime.now()
                    ));
        }
    }

    /**
     * Request object for sequence release
     */
    public static class ReleaseRequest {
        @NotNull
        @Min(1)
        private Long sequenceNumber;
        
        @NotBlank
        private String siteId;
        
        @NotBlank
        private String partitionId;
        
        @NotBlank
        private String reason;

        // Constructors
        public ReleaseRequest() {}

        public ReleaseRequest(Long sequenceNumber, String siteId, String partitionId, String reason) {
            this.sequenceNumber = sequenceNumber;
            this.siteId = siteId;
            this.partitionId = partitionId;
            this.reason = reason;
        }

        // Getters and setters
        public Long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

        public String getSiteId() { return siteId; }
        public void setSiteId(String siteId) { this.siteId = siteId; }

        public String getPartitionId() { return partitionId; }
        public void setPartitionId(String partitionId) { this.partitionId = partitionId; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        @Override
        public String toString() {
            return "ReleaseRequest{" +
                    "sequenceNumber=" + sequenceNumber +
                    ", siteId='" + siteId + '\'' +
                    ", partitionId='" + partitionId + '\'' +
                    ", reason='" + reason + '\'' +
                    '}';
        }
    }
}
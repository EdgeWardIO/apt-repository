package com.demo.sequence.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Audit Record for Sequence Number Operations
 * 
 * Maintains a complete audit trail of all sequence number generation,
 * release, and gap recovery operations for compliance and debugging.
 */
public class AuditRecord {

    public enum OperationType {
        GENERATE,    // New sequence number generated
        RELEASE,     // Sequence number released (creates gap)
        RECOVER,     // Gap recovered (reused sequence number)
        RESERVE,     // Sequence number reserved for future use
        EXPIRE       // Reserved sequence expired
    }

    public enum Status {
        ACTIVE,      // Sequence is currently in use
        RELEASED,    // Sequence was released and available for reuse
        RECOVERED,   // Gap was filled by reusing this sequence
        EXPIRED,     // Reserved sequence expired
        INVALID      // Sequence marked as invalid
    }

    // Core identifiers
    private String auditId;
    private long sequenceNumber;
    private String siteId;
    private String partitionId;
    private String invoiceType;

    // Operation details
    private OperationType operationType;
    private Status status;
    private String reason;
    private String description;

    // Timestamps
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime releaseTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime recoveryTime;

    // System details
    private String nodeId;
    private String instanceId;
    private String strategy;
    private boolean isGapFilled;
    private long globalCounter;

    // Request traceability
    private String requestId;
    private String correlationId;
    private long processingTimeMs;

    // Additional metadata
    private Map<String, Object> metadata;

    // Constructors
    public AuditRecord() {
        this.timestamp = LocalDateTime.now();
        this.auditId = generateAuditId();
        this.status = Status.ACTIVE;
    }

    public AuditRecord(long sequenceNumber, String siteId, String partitionId, String invoiceType, OperationType operationType) {
        this();
        this.sequenceNumber = sequenceNumber;
        this.siteId = siteId;
        this.partitionId = partitionId;
        this.invoiceType = invoiceType;
        this.operationType = operationType;
    }

    // Static factory methods
    public static AuditRecord forGeneration(long sequenceNumber, String siteId, String partitionId, String invoiceType) {
        AuditRecord record = new AuditRecord(sequenceNumber, siteId, partitionId, invoiceType, OperationType.GENERATE);
        record.setDescription("Sequence number generated");
        return record;
    }

    public static AuditRecord forRelease(long sequenceNumber, String siteId, String partitionId, String reason) {
        AuditRecord record = new AuditRecord();
        record.setSequenceNumber(sequenceNumber);
        record.setSiteId(siteId);
        record.setPartitionId(partitionId);
        record.setOperationType(OperationType.RELEASE);
        record.setStatus(Status.RELEASED);
        record.setReason(reason);
        record.setReleaseTime(LocalDateTime.now());
        record.setDescription("Sequence number released: " + reason);
        return record;
    }

    public static AuditRecord forRecovery(long sequenceNumber, String siteId, String partitionId, String invoiceType) {
        AuditRecord record = new AuditRecord(sequenceNumber, siteId, partitionId, invoiceType, OperationType.RECOVER);
        record.setStatus(Status.RECOVERED);
        record.setGapFilled(true);
        record.setRecoveryTime(LocalDateTime.now());
        record.setDescription("Gap recovered - sequence number reused");
        return record;
    }

    // Private helper methods
    private String generateAuditId() {
        return "AUDIT-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    // Getters and Setters
    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(String partitionId) {
        this.partitionId = partitionId;
    }

    public String getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(String invoiceType) {
        this.invoiceType = invoiceType;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(LocalDateTime releaseTime) {
        this.releaseTime = releaseTime;
    }

    public LocalDateTime getRecoveryTime() {
        return recoveryTime;
    }

    public void setRecoveryTime(LocalDateTime recoveryTime) {
        this.recoveryTime = recoveryTime;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public boolean isGapFilled() {
        return isGapFilled;
    }

    public void setGapFilled(boolean gapFilled) {
        isGapFilled = gapFilled;
    }

    public long getGlobalCounter() {
        return globalCounter;
    }

    public void setGlobalCounter(long globalCounter) {
        this.globalCounter = globalCounter;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Utility methods
    public String getSitePartitionKey() {
        return siteId + ":" + partitionId;
    }

    public String getFullKey() {
        return siteId + ":" + partitionId + ":" + invoiceType;
    }

    public boolean isReleased() {
        return status == Status.RELEASED;
    }

    public boolean isRecovered() {
        return status == Status.RECOVERED;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    @Override
    public String toString() {
        return "AuditRecord{" +
                "auditId='" + auditId + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", siteId='" + siteId + '\'' +
                ", partitionId='" + partitionId + '\'' +
                ", invoiceType='" + invoiceType + '\'' +
                ", operationType=" + operationType +
                ", status=" + status +
                ", timestamp=" + timestamp +
                ", isGapFilled=" + isGapFilled +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuditRecord that = (AuditRecord) o;

        return auditId.equals(that.auditId);
    }

    @Override
    public int hashCode() {
        return auditId.hashCode();
    }
}
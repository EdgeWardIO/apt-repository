package com.demo.sequence.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response object for sequence number generation
 * 
 * Contains the generated sequence numbers and metadata about the generation process.
 */
public class SequenceResponse {

    private List<Long> sequenceNumbers;
    private boolean success;
    private String error;
    private boolean isGapFilled;
    private long globalCounter;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    private String nodeId;
    private String instanceId;
    private SequenceStats stats;
    
    // Request details for traceability
    private String siteId;
    private String partitionId;
    private String invoiceType;
    private String requestId;
    
    // Performance metrics
    private long processingTimeMs;
    private String strategy;
    
    // Additional metadata
    private Map<String, Object> metadata;

    // Constructors
    public SequenceResponse() {
        this.timestamp = LocalDateTime.now();
        this.success = false;
    }

    public SequenceResponse(List<Long> sequenceNumbers) {
        this();
        this.sequenceNumbers = sequenceNumbers;
        this.success = true;
    }

    public SequenceResponse(String error) {
        this();
        this.error = error;
        this.success = false;
    }

    // Static factory methods
    public static SequenceResponse success(List<Long> sequenceNumbers, long globalCounter) {
        SequenceResponse response = new SequenceResponse(sequenceNumbers);
        response.setGlobalCounter(globalCounter);
        return response;
    }

    public static SequenceResponse success(List<Long> sequenceNumbers, long globalCounter, boolean isGapFilled) {
        SequenceResponse response = success(sequenceNumbers, globalCounter);
        response.setGapFilled(isGapFilled);
        return response;
    }

    public static SequenceResponse error(String errorMessage) {
        return new SequenceResponse(errorMessage);
    }

    // Getters and Setters
    public List<Long> getSequenceNumbers() {
        return sequenceNumbers;
    }

    public void setSequenceNumbers(List<Long> sequenceNumbers) {
        this.sequenceNumbers = sequenceNumbers;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
        this.success = false;
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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
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

    public SequenceStats getStats() {
        return stats;
    }

    public void setStats(SequenceStats stats) {
        this.stats = stats;
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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Utility methods
    public Long getFirstSequenceNumber() {
        return sequenceNumbers != null && !sequenceNumbers.isEmpty() ? sequenceNumbers.get(0) : null;
    }

    public int getSequenceCount() {
        return sequenceNumbers != null ? sequenceNumbers.size() : 0;
    }

    public SequenceResponse withRequestDetails(SequenceRequest request) {
        this.siteId = request.getSiteId();
        this.partitionId = request.getPartitionId();
        this.invoiceType = request.getInvoiceType();
        this.requestId = request.getRequestId();
        return this;
    }

    public SequenceResponse withPerformanceMetrics(long processingTimeMs, String strategy) {
        this.processingTimeMs = processingTimeMs;
        this.strategy = strategy;
        return this;
    }

    public SequenceResponse withInstanceInfo(String nodeId, String instanceId) {
        this.nodeId = nodeId;
        this.instanceId = instanceId;
        return this;
    }

    @Override
    public String toString() {
        return "SequenceResponse{" +
                "sequenceNumbers=" + sequenceNumbers +
                ", success=" + success +
                ", error='" + error + '\'' +
                ", isGapFilled=" + isGapFilled +
                ", globalCounter=" + globalCounter +
                ", timestamp=" + timestamp +
                ", nodeId='" + nodeId + '\'' +
                ", processingTimeMs=" + processingTimeMs +
                '}';
    }
}
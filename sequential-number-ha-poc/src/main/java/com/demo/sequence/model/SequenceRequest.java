package com.demo.sequence.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request object for sequence number generation
 * 
 * Represents a request to generate invoice numbers for a specific
 * site, partition, and invoice type combination.
 */
public class SequenceRequest {

    @NotBlank(message = "Site ID is required")
    private String siteId;

    @NotBlank(message = "Partition ID is required") 
    private String partitionId;

    @NotBlank(message = "Invoice type is required")
    private String invoiceType;

    @Positive(message = "Count must be positive")
    private Integer count = 1;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime requestTime;

    private String requestId;

    private Map<String, Object> metadata;

    // Constructors
    public SequenceRequest() {
        this.requestTime = LocalDateTime.now();
        this.requestId = generateRequestId();
    }

    public SequenceRequest(String siteId, String partitionId, String invoiceType) {
        this();
        this.siteId = siteId;
        this.partitionId = partitionId;
        this.invoiceType = invoiceType;
    }

    public SequenceRequest(String siteId, String partitionId, String invoiceType, Integer count) {
        this(siteId, partitionId, invoiceType);
        this.count = count;
    }

    // Private helper method
    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    // Getters and Setters
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

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    @Override
    public String toString() {
        return "SequenceRequest{" +
                "siteId='" + siteId + '\'' +
                ", partitionId='" + partitionId + '\'' +
                ", invoiceType='" + invoiceType + '\'' +
                ", count=" + count +
                ", requestTime=" + requestTime +
                ", requestId='" + requestId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SequenceRequest that = (SequenceRequest) o;

        if (!siteId.equals(that.siteId)) return false;
        if (!partitionId.equals(that.partitionId)) return false;
        if (!invoiceType.equals(that.invoiceType)) return false;
        return count.equals(that.count);
    }

    @Override
    public int hashCode() {
        int result = siteId.hashCode();
        result = 31 * result + partitionId.hashCode();
        result = 31 * result + invoiceType.hashCode();
        result = 31 * result + count.hashCode();
        return result;
    }
}
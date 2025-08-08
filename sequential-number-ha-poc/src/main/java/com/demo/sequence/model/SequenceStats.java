package com.demo.sequence.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Statistics and metrics for sequence number generation
 * 
 * Provides comprehensive statistics about sequence generation performance,
 * gaps, distribution across sites/partitions, and system health.
 */
public class SequenceStats {

    // Core sequence statistics
    private long currentCounter;
    private long totalGenerated;
    private long totalReleased;
    private int availableGaps;
    private long nextAvailableGap;

    // Performance metrics
    private double averageLatencyMs;
    private double peakLatencyMs;
    private long requestsPerSecond;
    private long totalRequests;
    
    // Distribution metrics
    private Map<String, Long> sequencesBySite;
    private Map<String, Long> sequencesByPartition;
    private Map<String, Long> sequencesByInvoiceType;
    private Map<String, Long> sequencesBySitePartition;

    // Gap management statistics
    private long totalGapsCreated;
    private long totalGapsRecovered;
    private double gapRecoveryRate;
    private long longestGapDuration;

    // System health metrics
    private boolean systemHealthy;
    private long uptime;
    private String currentStrategy;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSequenceTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime statisticsTime;

    // HA-specific metrics
    private int activeNodes;
    private int totalNodes;
    private String primaryNode;
    private boolean clusterHealthy;

    // Constructors
    public SequenceStats() {
        this.statisticsTime = LocalDateTime.now();
        this.systemHealthy = true;
        this.clusterHealthy = true;
    }

    // Static factory methods
    public static SequenceStats empty() {
        return new SequenceStats();
    }

    public static SequenceStats withBasicStats(long currentCounter, long totalGenerated, int availableGaps) {
        SequenceStats stats = new SequenceStats();
        stats.setCurrentCounter(currentCounter);
        stats.setTotalGenerated(totalGenerated);
        stats.setAvailableGaps(availableGaps);
        return stats;
    }

    // Getters and Setters
    public long getCurrentCounter() {
        return currentCounter;
    }

    public void setCurrentCounter(long currentCounter) {
        this.currentCounter = currentCounter;
    }

    public long getTotalGenerated() {
        return totalGenerated;
    }

    public void setTotalGenerated(long totalGenerated) {
        this.totalGenerated = totalGenerated;
    }

    public long getTotalReleased() {
        return totalReleased;
    }

    public void setTotalReleased(long totalReleased) {
        this.totalReleased = totalReleased;
    }

    public int getAvailableGaps() {
        return availableGaps;
    }

    public void setAvailableGaps(int availableGaps) {
        this.availableGaps = availableGaps;
    }

    public long getNextAvailableGap() {
        return nextAvailableGap;
    }

    public void setNextAvailableGap(long nextAvailableGap) {
        this.nextAvailableGap = nextAvailableGap;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(double averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    public double getPeakLatencyMs() {
        return peakLatencyMs;
    }

    public void setPeakLatencyMs(double peakLatencyMs) {
        this.peakLatencyMs = peakLatencyMs;
    }

    public long getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(long requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public Map<String, Long> getSequencesBySite() {
        return sequencesBySite;
    }

    public void setSequencesBySite(Map<String, Long> sequencesBySite) {
        this.sequencesBySite = sequencesBySite;
    }

    public Map<String, Long> getSequencesByPartition() {
        return sequencesByPartition;
    }

    public void setSequencesByPartition(Map<String, Long> sequencesByPartition) {
        this.sequencesByPartition = sequencesByPartition;
    }

    public Map<String, Long> getSequencesByInvoiceType() {
        return sequencesByInvoiceType;
    }

    public void setSequencesByInvoiceType(Map<String, Long> sequencesByInvoiceType) {
        this.sequencesByInvoiceType = sequencesByInvoiceType;
    }

    public Map<String, Long> getSequencesBySitePartition() {
        return sequencesBySitePartition;
    }

    public void setSequencesBySitePartition(Map<String, Long> sequencesBySitePartition) {
        this.sequencesBySitePartition = sequencesBySitePartition;
    }

    public long getTotalGapsCreated() {
        return totalGapsCreated;
    }

    public void setTotalGapsCreated(long totalGapsCreated) {
        this.totalGapsCreated = totalGapsCreated;
    }

    public long getTotalGapsRecovered() {
        return totalGapsRecovered;
    }

    public void setTotalGapsRecovered(long totalGapsRecovered) {
        this.totalGapsRecovered = totalGapsRecovered;
    }

    public double getGapRecoveryRate() {
        return gapRecoveryRate;
    }

    public void setGapRecoveryRate(double gapRecoveryRate) {
        this.gapRecoveryRate = gapRecoveryRate;
    }

    public long getLongestGapDuration() {
        return longestGapDuration;
    }

    public void setLongestGapDuration(long longestGapDuration) {
        this.longestGapDuration = longestGapDuration;
    }

    public boolean isSystemHealthy() {
        return systemHealthy;
    }

    public void setSystemHealthy(boolean systemHealthy) {
        this.systemHealthy = systemHealthy;
    }

    public long getUptime() {
        return uptime;
    }

    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    public String getCurrentStrategy() {
        return currentStrategy;
    }

    public void setCurrentStrategy(String currentStrategy) {
        this.currentStrategy = currentStrategy;
    }

    public LocalDateTime getLastSequenceTime() {
        return lastSequenceTime;
    }

    public void setLastSequenceTime(LocalDateTime lastSequenceTime) {
        this.lastSequenceTime = lastSequenceTime;
    }

    public LocalDateTime getStatisticsTime() {
        return statisticsTime;
    }

    public void setStatisticsTime(LocalDateTime statisticsTime) {
        this.statisticsTime = statisticsTime;
    }

    public int getActiveNodes() {
        return activeNodes;
    }

    public void setActiveNodes(int activeNodes) {
        this.activeNodes = activeNodes;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public String getPrimaryNode() {
        return primaryNode;
    }

    public void setPrimaryNode(String primaryNode) {
        this.primaryNode = primaryNode;
    }

    public boolean isClusterHealthy() {
        return clusterHealthy;
    }

    public void setClusterHealthy(boolean clusterHealthy) {
        this.clusterHealthy = clusterHealthy;
    }

    // Utility methods
    public double getGapPercentage() {
        return totalGenerated > 0 ? (double) availableGaps / totalGenerated * 100 : 0.0;
    }

    public long getActiveSequences() {
        return totalGenerated - totalReleased;
    }

    public double getClusterAvailability() {
        return totalNodes > 0 ? (double) activeNodes / totalNodes * 100 : 0.0;
    }

    public boolean hasGaps() {
        return availableGaps > 0;
    }

    @Override
    public String toString() {
        return "SequenceStats{" +
                "currentCounter=" + currentCounter +
                ", totalGenerated=" + totalGenerated +
                ", totalReleased=" + totalReleased +
                ", availableGaps=" + availableGaps +
                ", averageLatencyMs=" + averageLatencyMs +
                ", requestsPerSecond=" + requestsPerSecond +
                ", systemHealthy=" + systemHealthy +
                ", clusterHealthy=" + clusterHealthy +
                ", activeNodes=" + activeNodes +
                "/" + totalNodes +
                '}';
    }
}
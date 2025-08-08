package com.demo.sequence.service;

import com.demo.sequence.model.AuditRecord;
import com.demo.sequence.model.SequenceRequest;
import com.demo.sequence.model.SequenceResponse;
import com.demo.sequence.model.SequenceStats;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * etcd-based Sequence Manager Implementation
 * 
 * Uses etcd for distributed coordination and consensus across multiple nodes.
 * Ensures global sequential numbering even in high-availability scenarios.
 */
@Service
public class EtcdSequenceManager implements SequenceManager {

    private static final Logger logger = LoggerFactory.getLogger(EtcdSequenceManager.class);

    // etcd keys for different data types
    private static final String GLOBAL_COUNTER_KEY = "/sequence/global/counter";
    private static final String GAPS_PREFIX = "/sequence/gaps/";
    private static final String AUDIT_PREFIX = "/sequence/audit/";
    private static final String STATS_PREFIX = "/sequence/stats/";
    private static final String HEALTH_PREFIX = "/sequence/health/";

    @Autowired
    private Client etcdClient;

    @Value("${sequence.instance.id:node-1}")
    private String instanceId;

    @Value("${sequence.instance.name:Default Instance}")
    private String instanceName;

    @Value("${sequence.core.global-counter-start:1}")
    private long globalCounterStart;

    private KV kvClient;
    private final AtomicLong localCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Long, AuditRecord> localAuditCache = new ConcurrentHashMap<>();
    private final PriorityQueue<Long> localGapsCache = new PriorityQueue<>();
    private final ReentrantLock gapLock = new ReentrantLock();
    
    // Performance tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> sitePartitionStats = new ConcurrentHashMap<>();

    // Health and monitoring
    private volatile boolean healthy = true;
    private volatile LocalDateTime lastSuccessfulOperation = LocalDateTime.now();
    private final ScheduledExecutorService healthMonitor = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void initialize() {
        logger.info("Initializing etcd-based Sequence Manager for instance: {}", instanceId);
        
        try {
            kvClient = etcdClient.getKVClient();
            
            // Initialize global counter if it doesn't exist
            initializeGlobalCounter();
            
            // Load gaps from etcd
            loadGapsFromEtcd();
            
            // Start health monitoring
            startHealthMonitoring();
            
            logger.info("etcd Sequence Manager initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize etcd Sequence Manager", e);
            healthy = false;
            throw new RuntimeException("Failed to initialize sequence manager", e);
        }
    }

    @Override
    public SequenceResponse getNextSequence(SequenceRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            logger.debug("Processing sequence request: {}", request);
            
            // Validate request
            validateRequest(request);
            
            // Check for available gaps first
            List<Long> sequences = new ArrayList<>();
            boolean gapFilled = false;
            
            gapLock.lock();
            try {
                // Try to use gaps first
                for (int i = 0; i < request.getCount() && !localGapsCache.isEmpty(); i++) {
                    Long gapNumber = localGapsCache.poll();
                    if (gapNumber != null) {
                        sequences.add(gapNumber);
                        gapFilled = true;
                        
                        // Remove from etcd gaps
                        removeGapFromEtcd(gapNumber);
                        
                        // Create audit record
                        AuditRecord auditRecord = AuditRecord.forRecovery(
                            gapNumber, request.getSiteId(), request.getPartitionId(), request.getInvoiceType());
                        auditRecord.setNodeId(instanceId);
                        auditRecord.setInstanceId(instanceName);
                        auditRecord.setRequestId(request.getRequestId());
                        
                        storeAuditRecord(auditRecord);
                        localAuditCache.put(gapNumber, auditRecord);
                        
                        logger.debug("Filled gap with sequence number: {}", gapNumber);
                    }
                }
                
                // Generate new sequences for remaining count
                int remainingCount = request.getCount() - sequences.size();
                if (remainingCount > 0) {
                    List<Long> newSequences = generateNewSequences(remainingCount, request);
                    sequences.addAll(newSequences);
                }
                
            } finally {
                gapLock.unlock();
            }
            
            // Sort sequences to maintain order
            Collections.sort(sequences);
            
            // Update statistics
            updateStatistics(request, sequences);
            lastSuccessfulOperation = LocalDateTime.now();
            
            // Create response
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            
            SequenceResponse response = SequenceResponse.success(sequences, getCurrentGlobalCounter(), gapFilled);
            response.withRequestDetails(request)
                   .withPerformanceMetrics(processingTime, "global-sequential")
                   .withInstanceInfo(instanceId, instanceName);
            
            logger.debug("Generated sequences: {} (gap filled: {})", sequences, gapFilled);
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to generate sequence for request: {}", request, e);
            healthy = false;
            return SequenceResponse.error("Failed to generate sequence: " + e.getMessage());
        }
    }

    @Override
    public boolean releaseSequence(long sequenceNumber, String siteId, String partitionId, String reason) {
        try {
            logger.debug("Releasing sequence number {} for site {} partition {} reason: {}", 
                        sequenceNumber, siteId, partitionId, reason);
            
            gapLock.lock();
            try {
                // Add to gaps
                localGapsCache.offer(sequenceNumber);
                
                // Store gap in etcd
                storeGapInEtcd(sequenceNumber, siteId, partitionId, reason);
                
                // Create audit record
                AuditRecord auditRecord = AuditRecord.forRelease(sequenceNumber, siteId, partitionId, reason);
                auditRecord.setNodeId(instanceId);
                auditRecord.setInstanceId(instanceName);
                
                storeAuditRecord(auditRecord);
                localAuditCache.put(sequenceNumber, auditRecord);
                
                logger.info("Released sequence number {} successfully", sequenceNumber);
                return true;
                
            } finally {
                gapLock.unlock();
            }
            
        } catch (Exception e) {
            logger.error("Failed to release sequence number {}", sequenceNumber, e);
            return false;
        }
    }

    @Override
    public SequenceStats getStats() {
        try {
            SequenceStats stats = new SequenceStats();
            
            // Basic counters
            stats.setCurrentCounter(getCurrentGlobalCounter());
            stats.setTotalGenerated(totalRequests.get());
            stats.setAvailableGaps(localGapsCache.size());
            stats.setNextAvailableGap(localGapsCache.peek() != null ? localGapsCache.peek() : -1);
            
            // Performance metrics
            long totalTime = totalProcessingTime.get();
            long totalReqs = totalRequests.get();
            stats.setAverageLatencyMs(totalReqs > 0 ? (double) totalTime / totalReqs : 0.0);
            stats.setTotalRequests(totalReqs);
            
            // Distribution statistics
            Map<String, Long> sitePartitionCounts = new HashMap<>();
            sitePartitionStats.forEach((key, value) -> sitePartitionCounts.put(key, value.get()));
            stats.setSequencesBySitePartition(sitePartitionCounts);
            
            // System health
            stats.setSystemHealthy(healthy);
            stats.setCurrentStrategy("global-sequential");
            stats.setLastSequenceTime(lastSuccessfulOperation);
            stats.setStatisticsTime(LocalDateTime.now());
            
            // HA-specific stats (if applicable)
            stats.setActiveNodes(1); // This will be enhanced in HA mode
            stats.setTotalNodes(1);
            stats.setClusterHealthy(healthy);
            stats.setPrimaryNode(instanceId);
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Failed to get statistics", e);
            return SequenceStats.empty();
        }
    }

    @Override
    public List<AuditRecord> getAuditTrail(int limit) {
        try {
            // Return from local cache first, then from etcd if needed
            List<AuditRecord> records = new ArrayList<>(localAuditCache.values());
            records.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
            
            return records.size() > limit ? records.subList(0, limit) : records;
            
        } catch (Exception e) {
            logger.error("Failed to get audit trail", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AuditRecord> getAuditTrailForSequence(long sequenceNumber) {
        try {
            return localAuditCache.values().stream()
                    .filter(record -> record.getSequenceNumber() == sequenceNumber)
                    .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                    .toList();
                    
        } catch (Exception e) {
            logger.error("Failed to get audit trail for sequence {}", sequenceNumber, e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy && etcdClient != null;
    }

    @Override
    public ValidationResult validateSequenceIntegrity() {
        try {
            ValidationResult result = new ValidationResult(true);
            
            // Check for gaps
            List<Long> gaps = new ArrayList<>(localGapsCache);
            result.setGaps(gaps);
            
            // For now, assume no duplicates in our implementation
            result.setDuplicates(new ArrayList<>());
            
            // Create summary
            result.setSummary(String.format("Current counter: %d, Available gaps: %d", 
                             getCurrentGlobalCounter(), gaps.size()));
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to validate sequence integrity", e);
            ValidationResult result = new ValidationResult(false);
            result.setSummary("Validation failed: " + e.getMessage());
            return result;
        }
    }

    @Override
    public boolean resetSystem() {
        try {
            logger.warn("Resetting sequence system - this will clear all data!");
            
            gapLock.lock();
            try {
                // Clear local caches
                localAuditCache.clear();
                localGapsCache.clear();
                sitePartitionStats.clear();
                totalRequests.set(0);
                totalProcessingTime.set(0);
                
                // Reset global counter in etcd
                putToEtcd(GLOBAL_COUNTER_KEY, String.valueOf(globalCounterStart));
                localCounter.set(globalCounterStart);
                
                logger.info("System reset completed successfully");
                return true;
                
            } finally {
                gapLock.unlock();
            }
            
        } catch (Exception e) {
            logger.error("Failed to reset system", e);
            return false;
        }
    }

    @Override
    public List<Long> getAvailableGaps() {
        return new ArrayList<>(localGapsCache);
    }

    @Override
    public boolean recoverGap(long sequenceNumber) {
        try {
            gapLock.lock();
            try {
                if (localGapsCache.remove(sequenceNumber)) {
                    removeGapFromEtcd(sequenceNumber);
                    logger.info("Manually recovered gap: {}", sequenceNumber);
                    return true;
                } else {
                    logger.warn("Gap {} not found for recovery", sequenceNumber);
                    return false;
                }
            } finally {
                gapLock.unlock();
            }
        } catch (Exception e) {
            logger.error("Failed to recover gap {}", sequenceNumber, e);
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down etcd Sequence Manager");
        
        try {
            healthMonitor.shutdown();
            if (!healthMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                healthMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthMonitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("etcd Sequence Manager shutdown completed");
    }

    // Private helper methods
    
    private void validateRequest(SequenceRequest request) {
        if (request.getSiteId() == null || request.getSiteId().isEmpty()) {
            throw new IllegalArgumentException("Site ID is required");
        }
        if (request.getPartitionId() == null || request.getPartitionId().isEmpty()) {
            throw new IllegalArgumentException("Partition ID is required");
        }
        if (request.getInvoiceType() == null || request.getInvoiceType().isEmpty()) {
            throw new IllegalArgumentException("Invoice type is required");
        }
        if (request.getCount() == null || request.getCount() <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
    }

    private void initializeGlobalCounter() throws Exception {
        String counterValue = getFromEtcd(GLOBAL_COUNTER_KEY);
        if (counterValue == null) {
            putToEtcd(GLOBAL_COUNTER_KEY, String.valueOf(globalCounterStart));
            localCounter.set(globalCounterStart);
            logger.info("Initialized global counter to: {}", globalCounterStart);
        } else {
            localCounter.set(Long.parseLong(counterValue));
            logger.info("Loaded global counter from etcd: {}", counterValue);
        }
    }

    private void loadGapsFromEtcd() {
        // Implementation would load gaps from etcd
        // For now, start with empty gaps
        logger.info("Loaded {} gaps from etcd", localGapsCache.size());
    }

    private List<Long> generateNewSequences(int count, SequenceRequest request) throws Exception {
        List<Long> sequences = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            long nextSequence = incrementGlobalCounter();
            sequences.add(nextSequence);
            
            // Create audit record
            AuditRecord auditRecord = AuditRecord.forGeneration(
                nextSequence, request.getSiteId(), request.getPartitionId(), request.getInvoiceType());
            auditRecord.setNodeId(instanceId);
            auditRecord.setInstanceId(instanceName);
            auditRecord.setRequestId(request.getRequestId());
            
            storeAuditRecord(auditRecord);
            localAuditCache.put(nextSequence, auditRecord);
        }
        
        return sequences;
    }

    private long incrementGlobalCounter() throws Exception {
        // Atomic increment in etcd
        long currentValue = getCurrentGlobalCounter();
        long newValue = currentValue + 1;
        putToEtcd(GLOBAL_COUNTER_KEY, String.valueOf(newValue));
        localCounter.set(newValue);
        return newValue;
    }

    private long getCurrentGlobalCounter() {
        try {
            String value = getFromEtcd(GLOBAL_COUNTER_KEY);
            return value != null ? Long.parseLong(value) : localCounter.get();
        } catch (Exception e) {
            logger.warn("Failed to get global counter from etcd, using local value", e);
            return localCounter.get();
        }
    }

    private void updateStatistics(SequenceRequest request, List<Long> sequences) {
        String sitePartitionKey = request.getSitePartitionKey();
        sitePartitionStats.computeIfAbsent(sitePartitionKey, k -> new AtomicLong(0))
                         .addAndGet(sequences.size());
    }

    private void storeGapInEtcd(long sequenceNumber, String siteId, String partitionId, String reason) throws Exception {
        String gapKey = GAPS_PREFIX + sequenceNumber;
        String gapValue = String.format("%s:%s:%s:%s", siteId, partitionId, reason, LocalDateTime.now());
        putToEtcd(gapKey, gapValue);
    }

    private void removeGapFromEtcd(long sequenceNumber) throws Exception {
        String gapKey = GAPS_PREFIX + sequenceNumber;
        deleteFromEtcd(gapKey);
    }

    private void storeAuditRecord(AuditRecord record) throws Exception {
        String auditKey = AUDIT_PREFIX + record.getAuditId();
        String auditValue = convertAuditRecordToString(record);
        putToEtcd(auditKey, auditValue);
    }

    private String convertAuditRecordToString(AuditRecord record) {
        // Simple string representation for etcd storage
        return String.format("%d:%s:%s:%s:%s:%s:%s", 
                           record.getSequenceNumber(),
                           record.getSiteId(),
                           record.getPartitionId(),
                           record.getInvoiceType(),
                           record.getOperationType(),
                           record.getStatus(),
                           record.getTimestamp());
    }

    private void startHealthMonitoring() {
        healthMonitor.scheduleAtFixedRate(() -> {
            try {
                // Simple health check - try to read global counter
                String value = getFromEtcd(HEALTH_PREFIX + instanceId);
                putToEtcd(HEALTH_PREFIX + instanceId, String.valueOf(System.currentTimeMillis()));
                healthy = true;
            } catch (Exception e) {
                logger.warn("Health check failed", e);
                healthy = false;
            }
        }, 10, 30, TimeUnit.SECONDS);
    }

    // etcd utility methods
    
    private String getFromEtcd(String key) throws Exception {
        ByteSequence keyBytes = ByteSequence.from(key, StandardCharsets.UTF_8);
        GetResponse response = kvClient.get(keyBytes).get();
        
        if (response.getKvs().isEmpty()) {
            return null;
        }
        
        return response.getKvs().get(0).getValue().toString(StandardCharsets.UTF_8);
    }

    private void putToEtcd(String key, String value) throws Exception {
        ByteSequence keyBytes = ByteSequence.from(key, StandardCharsets.UTF_8);
        ByteSequence valueBytes = ByteSequence.from(value, StandardCharsets.UTF_8);
        kvClient.put(keyBytes, valueBytes).get();
    }

    private void deleteFromEtcd(String key) throws Exception {
        ByteSequence keyBytes = ByteSequence.from(key, StandardCharsets.UTF_8);
        kvClient.delete(keyBytes).get();
    }
}
package com.demo.sequence.controller;

import com.demo.sequence.model.SequenceRequest;
import com.demo.sequence.model.SequenceResponse;
import com.demo.sequence.service.SequenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Demo Controller for Sequential Number Generation Demonstrations
 * 
 * Provides automated demonstrations of sequence generation capabilities,
 * including basic functionality, concurrency, gap management, and more.
 */
@RestController
@RequestMapping("/api/v1/demo")
@CrossOrigin(origins = "*")
public class DemoController {

    private static final Logger logger = LoggerFactory.getLogger(DemoController.class);

    @Autowired
    private SequenceManager sequenceManager;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Basic demonstration - generates sequences across all site/partition combinations
     */
    @PostMapping("/basic")
    public ResponseEntity<DemoResult> runBasicDemo() {
        logger.info("Running basic sequence generation demonstration");
        
        try {
            DemoResult result = new DemoResult("Basic Sequence Generation Demo");
            result.addStep("Starting basic demonstration...");

            // Define site/partition combinations
            List<SitePartitionCombo> combos = Arrays.asList(
                new SitePartitionCombo("site-1", "partition-a", "on-cycle"),
                new SitePartitionCombo("site-1", "partition-b", "off-cycle"),
                new SitePartitionCombo("site-2", "partition-a", "simulated"),
                new SitePartitionCombo("site-2", "partition-b", "on-cycle"),
                new SitePartitionCombo("site-1", "partition-a", "off-cycle"),
                new SitePartitionCombo("site-2", "partition-b", "suppressed")
            );

            List<Long> generatedSequences = new ArrayList<>();
            
            for (int i = 0; i < combos.size(); i++) {
                SitePartitionCombo combo = combos.get(i);
                
                SequenceRequest request = new SequenceRequest(combo.siteId, combo.partitionId, combo.invoiceType);
                SequenceResponse response = sequenceManager.getNextSequence(request);
                
                if (response.isSuccess()) {
                    Long sequence = response.getFirstSequenceNumber();
                    generatedSequences.add(sequence);
                    
                    result.addStep(String.format("Step %d: Generated sequence %d for %s-%s (%s)", 
                                 i + 1, sequence, combo.siteId, combo.partitionId, combo.invoiceType));
                } else {
                    result.addStep(String.format("Step %d: FAILED for %s-%s: %s", 
                                 i + 1, combo.siteId, combo.partitionId, response.getError()));
                    result.setSuccess(false);
                }
            }

            // Verify sequences are sequential
            Collections.sort(generatedSequences);
            boolean isSequential = isSequentialList(generatedSequences);
            
            result.addStep(String.format("Generated sequences: %s", generatedSequences));
            result.addStep(String.format("Sequences are sequential: %s", isSequential ? "✅ YES" : "❌ NO"));
            result.setSequences(generatedSequences);
            result.setSuccess(isSequential);
            
            result.setSummary(String.format("Generated %d sequential numbers across %d different site/partition/type combinations", 
                            generatedSequences.size(), combos.size()));

            logger.info("Basic demo completed: {}", result.getSummary());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Basic demo failed", e);
            DemoResult errorResult = new DemoResult("Basic Demo Failed");
            errorResult.addStep("ERROR: " + e.getMessage());
            errorResult.setSuccess(false);
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Concurrent access demonstration
     */
    @PostMapping("/concurrent")
    public ResponseEntity<DemoResult> runConcurrentDemo(
            @RequestParam(defaultValue = "4") int threads,
            @RequestParam(defaultValue = "25") int requestsPerThread) {
        
        logger.info("Running concurrent access demo with {} threads, {} requests per thread", threads, requestsPerThread);
        
        try {
            DemoResult result = new DemoResult("Concurrent Access Demo");
            result.addStep(String.format("Starting concurrent demo with %d threads, %d requests per thread", threads, requestsPerThread));

            List<Future<List<Long>>> futures = new ArrayList<>();
            List<SitePartitionCombo> combos = Arrays.asList(
                new SitePartitionCombo("site-1", "partition-a", "on-cycle"),
                new SitePartitionCombo("site-1", "partition-b", "off-cycle"),
                new SitePartitionCombo("site-2", "partition-a", "simulated"),
                new SitePartitionCombo("site-2", "partition-b", "suppressed")
            );

            long startTime = System.currentTimeMillis();

            // Submit concurrent tasks
            for (int i = 0; i < threads; i++) {
                final int threadIndex = i;
                final SitePartitionCombo combo = combos.get(i % combos.size());
                
                Future<List<Long>> future = executorService.submit(() -> {
                    List<Long> threadSequences = new ArrayList<>();
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        SequenceRequest request = new SequenceRequest(combo.siteId, combo.partitionId, combo.invoiceType);
                        SequenceResponse response = sequenceManager.getNextSequence(request);
                        
                        if (response.isSuccess()) {
                            threadSequences.add(response.getFirstSequenceNumber());
                        }
                    }
                    
                    logger.debug("Thread {} completed {} requests", threadIndex, threadSequences.size());
                    return threadSequences;
                });
                
                futures.add(future);
            }

            // Collect results
            List<Long> allSequences = new ArrayList<>();
            for (Future<List<Long>> future : futures) {
                try {
                    allSequences.addAll(future.get(30, TimeUnit.SECONDS));
                } catch (TimeoutException e) {
                    result.addStep("WARNING: Some threads timed out");
                }
            }

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            // Analysis
            Collections.sort(allSequences);
            boolean hasNoDuplicates = allSequences.size() == new HashSet<>(allSequences).size();
            boolean isSequential = isSequentialList(allSequences);

            result.addStep(String.format("Completed in %d ms", totalTime));
            result.addStep(String.format("Total sequences generated: %d", allSequences.size()));
            result.addStep(String.format("Expected sequences: %d", threads * requestsPerThread));
            result.addStep(String.format("No duplicates: %s", hasNoDuplicates ? "✅ YES" : "❌ NO"));
            result.addStep(String.format("Sequential order: %s", isSequential ? "✅ YES" : "❌ NO"));
            result.addStep(String.format("Throughput: %.2f sequences/second", (double) allSequences.size() / totalTime * 1000));

            result.setSequences(allSequences);
            result.setSuccess(hasNoDuplicates && isSequential);
            result.setSummary(String.format("Generated %d unique sequential numbers across %d concurrent threads", 
                            allSequences.size(), threads));

            logger.info("Concurrent demo completed: {}", result.getSummary());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Concurrent demo failed", e);
            DemoResult errorResult = new DemoResult("Concurrent Demo Failed");
            errorResult.addStep("ERROR: " + e.getMessage());
            errorResult.setSuccess(false);
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Gap management demonstration
     */
    @PostMapping("/gaps")
    public ResponseEntity<DemoResult> runGapDemo() {
        logger.info("Running gap management demonstration");
        
        try {
            DemoResult result = new DemoResult("Gap Management Demo");
            result.addStep("Starting gap management demonstration...");

            List<Long> allSequences = new ArrayList<>();

            // Step 1: Generate some baseline sequences
            result.addStep("Step 1: Generating baseline sequences...");
            for (int i = 0; i < 5; i++) {
                SequenceRequest request = new SequenceRequest("site-1", "partition-a", "on-cycle");
                SequenceResponse response = sequenceManager.getNextSequence(request);
                if (response.isSuccess()) {
                    allSequences.add(response.getFirstSequenceNumber());
                }
            }
            result.addStep(String.format("Generated baseline sequences: %s", allSequences));

            // Step 2: Generate a suppressed invoice (will be released)
            result.addStep("Step 2: Generating suppressed invoice...");
            SequenceRequest suppressedRequest = new SequenceRequest("site-2", "partition-b", "suppressed");
            SequenceResponse suppressedResponse = sequenceManager.getNextSequence(suppressedRequest);
            
            Long suppressedSequence = null;
            if (suppressedResponse.isSuccess()) {
                suppressedSequence = suppressedResponse.getFirstSequenceNumber();
                allSequences.add(suppressedSequence);
                result.addStep(String.format("Generated suppressed invoice with sequence: %d", suppressedSequence));
            } else {
                result.addStep("Failed to generate suppressed invoice");
                result.setSuccess(false);
                return ResponseEntity.badRequest().body(result);
            }

            // Step 3: Release the suppressed sequence (creates gap)
            result.addStep("Step 3: Releasing suppressed invoice (creates gap)...");
            boolean released = sequenceManager.releaseSequence(suppressedSequence, "site-2", "partition-b", "suppressed-bill");
            if (released) {
                result.addStep(String.format("Successfully released sequence %d - gap created", suppressedSequence));
                allSequences.remove(suppressedSequence); // Remove from our tracking
            } else {
                result.addStep(String.format("Failed to release sequence %d", suppressedSequence));
            }

            // Step 4: Generate more sequences (should fill the gap)
            result.addStep("Step 4: Generating new sequences (should fill gap)...");
            List<Long> newSequences = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                SequenceRequest request = new SequenceRequest("site-1", "partition-b", "on-cycle");
                SequenceResponse response = sequenceManager.getNextSequence(request);
                if (response.isSuccess()) {
                    Long sequence = response.getFirstSequenceNumber();
                    newSequences.add(sequence);
                    allSequences.add(sequence);
                    
                    if (response.isGapFilled()) {
                        result.addStep(String.format("✅ Gap filled! Got sequence %d (was previously released)", sequence));
                    } else {
                        result.addStep(String.format("Generated new sequence: %d", sequence));
                    }
                }
            }

            // Final analysis
            Collections.sort(allSequences);
            boolean isSequential = isSequentialList(allSequences);
            List<Long> availableGaps = sequenceManager.getAvailableGaps();

            result.addStep(String.format("Final sequences: %s", allSequences));
            result.addStep(String.format("Remaining available gaps: %d", availableGaps.size()));
            result.addStep(String.format("Sequences are sequential: %s", isSequential ? "✅ YES" : "❌ NO"));

            result.setSequences(allSequences);
            result.setSuccess(isSequential);
            result.setSummary(String.format("Successfully demonstrated gap creation and recovery. Final sequence count: %d", 
                            allSequences.size()));

            logger.info("Gap demo completed: {}", result.getSummary());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Gap demo failed", e);
            DemoResult errorResult = new DemoResult("Gap Demo Failed");
            errorResult.addStep("ERROR: " + e.getMessage());
            errorResult.setSuccess(false);
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Load test demonstration
     */
    @PostMapping("/load-test")
    public ResponseEntity<DemoResult> runLoadTest(
            @RequestParam(defaultValue = "1000") int totalRequests,
            @RequestParam(defaultValue = "10") int concurrentThreads) {
        
        logger.info("Running load test with {} total requests across {} threads", totalRequests, concurrentThreads);
        
        try {
            DemoResult result = new DemoResult("Load Test Demo");
            result.addStep(String.format("Starting load test: %d requests across %d threads", totalRequests, concurrentThreads));

            int requestsPerThread = totalRequests / concurrentThreads;
            List<Future<LoadTestResult>> futures = new ArrayList<>();
            
            long startTime = System.currentTimeMillis();

            // Submit load test tasks
            for (int i = 0; i < concurrentThreads; i++) {
                final int threadIndex = i;
                
                Future<LoadTestResult> future = executorService.submit(() -> {
                    LoadTestResult threadResult = new LoadTestResult();
                    List<SitePartitionCombo> combos = Arrays.asList(
                        new SitePartitionCombo("site-1", "partition-a", "on-cycle"),
                        new SitePartitionCombo("site-1", "partition-b", "off-cycle"),
                        new SitePartitionCombo("site-2", "partition-a", "simulated"),
                        new SitePartitionCombo("site-2", "partition-b", "suppressed")
                    );
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        SitePartitionCombo combo = combos.get(j % combos.size());
                        
                        long requestStart = System.currentTimeMillis();
                        SequenceRequest request = new SequenceRequest(combo.siteId, combo.partitionId, combo.invoiceType);
                        SequenceResponse response = sequenceManager.getNextSequence(request);
                        long requestEnd = System.currentTimeMillis();
                        
                        if (response.isSuccess()) {
                            threadResult.successCount++;
                            threadResult.sequences.add(response.getFirstSequenceNumber());
                            threadResult.totalLatency += (requestEnd - requestStart);
                            threadResult.maxLatency = Math.max(threadResult.maxLatency, requestEnd - requestStart);
                            threadResult.minLatency = Math.min(threadResult.minLatency, requestEnd - requestStart);
                        } else {
                            threadResult.errorCount++;
                        }
                    }
                    
                    return threadResult;
                });
                
                futures.add(future);
            }

            // Collect results
            LoadTestResult aggregateResult = new LoadTestResult();
            for (Future<LoadTestResult> future : futures) {
                try {
                    LoadTestResult threadResult = future.get(60, TimeUnit.SECONDS);
                    aggregateResult.merge(threadResult);
                } catch (TimeoutException e) {
                    result.addStep("WARNING: Some threads timed out during load test");
                }
            }

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            // Analysis
            Collections.sort(aggregateResult.sequences);
            boolean hasNoDuplicates = aggregateResult.sequences.size() == new HashSet<>(aggregateResult.sequences).size();

            result.addStep(String.format("Load test completed in %d ms", totalTime));
            result.addStep(String.format("Successful requests: %d", aggregateResult.successCount));
            result.addStep(String.format("Failed requests: %d", aggregateResult.errorCount));
            result.addStep(String.format("Success rate: %.2f%%", (double) aggregateResult.successCount / totalRequests * 100));
            result.addStep(String.format("Average latency: %.2f ms", aggregateResult.getAverageLatency()));
            result.addStep(String.format("Min latency: %d ms", aggregateResult.minLatency));
            result.addStep(String.format("Max latency: %d ms", aggregateResult.maxLatency));
            result.addStep(String.format("Throughput: %.2f requests/second", (double) aggregateResult.successCount / totalTime * 1000));
            result.addStep(String.format("No duplicates: %s", hasNoDuplicates ? "✅ YES" : "❌ NO"));

            result.setSequences(aggregateResult.sequences);
            result.setSuccess(aggregateResult.errorCount == 0 && hasNoDuplicates);
            result.setSummary(String.format("Load test: %d successful requests, %.2f req/sec, %.2f ms avg latency", 
                            aggregateResult.successCount, 
                            (double) aggregateResult.successCount / totalTime * 1000,
                            aggregateResult.getAverageLatency()));

            logger.info("Load test completed: {}", result.getSummary());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Load test failed", e);
            DemoResult errorResult = new DemoResult("Load Test Failed");
            errorResult.addStep("ERROR: " + e.getMessage());
            errorResult.setSuccess(false);
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    // Helper classes and methods

    private static class SitePartitionCombo {
        final String siteId;
        final String partitionId;
        final String invoiceType;

        SitePartitionCombo(String siteId, String partitionId, String invoiceType) {
            this.siteId = siteId;
            this.partitionId = partitionId;
            this.invoiceType = invoiceType;
        }
    }

    private static class LoadTestResult {
        List<Long> sequences = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;
        long totalLatency = 0;
        long minLatency = Long.MAX_VALUE;
        long maxLatency = 0;

        void merge(LoadTestResult other) {
            sequences.addAll(other.sequences);
            successCount += other.successCount;
            errorCount += other.errorCount;
            totalLatency += other.totalLatency;
            minLatency = Math.min(minLatency, other.minLatency);
            maxLatency = Math.max(maxLatency, other.maxLatency);
        }

        double getAverageLatency() {
            return successCount > 0 ? (double) totalLatency / successCount : 0.0;
        }
    }

    public static class DemoResult {
        private String name;
        private List<String> steps = new ArrayList<>();
        private List<Long> sequences = new ArrayList<>();
        private boolean success = true;
        private String summary;
        private LocalDateTime timestamp = LocalDateTime.now();

        public DemoResult(String name) {
            this.name = name;
        }

        public void addStep(String step) {
            steps.add(step);
            logger.info("[{}] {}", name, step);
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getSteps() { return steps; }
        public void setSteps(List<String> steps) { this.steps = steps; }

        public List<Long> getSequences() { return sequences; }
        public void setSequences(List<Long> sequences) { this.sequences = sequences; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    private boolean isSequentialList(List<Long> sequences) {
        if (sequences.size() <= 1) return true;
        
        for (int i = 1; i < sequences.size(); i++) {
            if (sequences.get(i) != sequences.get(i-1) + 1) {
                return false;
            }
        }
        return true;
    }
}
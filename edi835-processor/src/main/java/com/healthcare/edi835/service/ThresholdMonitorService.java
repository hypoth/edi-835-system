package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled service for monitoring bucket thresholds.
 * Periodically evaluates all ACCUMULATING buckets against their thresholds.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Run scheduled threshold evaluations</li>
 *   <li>Check all active buckets</li>
 *   <li>Trigger state transitions when thresholds are met</li>
 *   <li>Log monitoring activities</li>
 * </ul>
 */
@Slf4j
@Service
public class ThresholdMonitorService {

    private final EdiFileBucketRepository bucketRepository;
    private final BucketManagerService bucketManagerService;

    public ThresholdMonitorService(
            EdiFileBucketRepository bucketRepository,
            BucketManagerService bucketManagerService) {
        this.bucketRepository = bucketRepository;
        this.bucketManagerService = bucketManagerService;
    }

    /**
     * Evaluates thresholds for all accumulating buckets.
     * Runs every 5 minutes (configurable via application.yml).
     */
    @Scheduled(fixedDelayString = "${edi835.threshold.monitor.interval:300000}",
               initialDelayString = "${edi835.threshold.monitor.initial-delay:60000}")
    @Transactional
    public void evaluateAllBucketThresholds() {
        log.info("Starting scheduled threshold evaluation");

        try {
            List<EdiFileBucket> activeBuckets = bucketRepository.findActiveBuckets();

            if (activeBuckets.isEmpty()) {
                log.debug("No active buckets found for threshold evaluation");
                return;
            }

            log.info("Evaluating {} active buckets", activeBuckets.size());

            int evaluatedCount = 0;
            int transitionedCount = 0;

            for (EdiFileBucket bucket : activeBuckets) {
                try {
                    EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

                    bucketManagerService.evaluateBucketThresholds(bucket);
                    evaluatedCount++;

                    // Refresh bucket to check if status changed
                    EdiFileBucket refreshedBucket = bucketRepository.findById(bucket.getBucketId())
                            .orElse(bucket);

                    if (refreshedBucket.getStatus() != previousStatus) {
                        transitionedCount++;
                        log.info("Bucket {} transitioned from {} to {}",
                                bucket.getBucketId(), previousStatus, refreshedBucket.getStatus());
                    }

                } catch (Exception e) {
                    log.error("Error evaluating bucket {}: {}",
                            bucket.getBucketId(), e.getMessage(), e);
                    // Continue with next bucket
                }
            }

            log.info("Threshold evaluation complete: evaluated={}, transitioned={}",
                    evaluatedCount, transitionedCount);

        } catch (Exception e) {
            log.error("Error during threshold evaluation cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Evaluates time-based thresholds for all buckets.
     * Runs daily at 2 AM (configurable via application.yml).
     */
    @Scheduled(cron = "${edi835.threshold.monitor.time-based.cron:0 0 2 * * ?}")
    @Transactional
    public void evaluateTimeBasedThresholds() {
        log.info("Starting scheduled time-based threshold evaluation");

        try {
            List<EdiFileBucket> activeBuckets = bucketRepository.findActiveBuckets();

            if (activeBuckets.isEmpty()) {
                log.debug("No active buckets found for time-based threshold evaluation");
                return;
            }

            log.info("Evaluating time-based thresholds for {} active buckets", activeBuckets.size());

            int transitionedCount = 0;

            for (EdiFileBucket bucket : activeBuckets) {
                try {
                    EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

                    // Evaluate thresholds (including time-based)
                    bucketManagerService.evaluateBucketThresholds(bucket);

                    // Refresh bucket to check if status changed
                    EdiFileBucket refreshedBucket = bucketRepository.findById(bucket.getBucketId())
                            .orElse(bucket);

                    if (refreshedBucket.getStatus() != previousStatus) {
                        transitionedCount++;
                        log.info("Time-based threshold met for bucket {}: {} -> {}",
                                bucket.getBucketId(), previousStatus, refreshedBucket.getStatus());
                    }

                } catch (Exception e) {
                    log.error("Error evaluating time-based threshold for bucket {}: {}",
                            bucket.getBucketId(), e.getMessage(), e);
                }
            }

            log.info("Time-based threshold evaluation complete: transitioned={}", transitionedCount);

        } catch (Exception e) {
            log.error("Error during time-based threshold evaluation: {}", e.getMessage(), e);
        }
    }

    /**
     * Monitors pending approval buckets and logs their status.
     * Runs every hour (configurable via application.yml).
     */
    @Scheduled(fixedDelayString = "${edi835.threshold.monitor.pending-approval.interval:3600000}",
               initialDelayString = "${edi835.threshold.monitor.pending-approval.initial-delay:300000}")
    public void monitorPendingApprovals() {
        log.debug("Checking pending approval buckets");

        try {
            List<EdiFileBucket> pendingBuckets = bucketRepository.findPendingApprovals();

            if (pendingBuckets.isEmpty()) {
                log.debug("No buckets pending approval");
                return;
            }

            log.info("Found {} buckets pending approval", pendingBuckets.size());

            for (EdiFileBucket bucket : pendingBuckets) {
                log.info("Bucket {} pending approval: payer={}, payee={}, claims={}, amount={}, waiting_since={}",
                        bucket.getBucketId(),
                        bucket.getPayerName(),
                        bucket.getPayeeName(),
                        bucket.getClaimCount(),
                        bucket.getTotalAmount(),
                        bucket.getAwaitingApprovalSince());
            }

        } catch (Exception e) {
            log.error("Error monitoring pending approvals: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up stale buckets that have been accumulating for too long.
     * Runs daily at 3 AM (configurable via application.yml).
     */
    @Scheduled(cron = "${edi835.threshold.monitor.cleanup.cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupStaleBuckets() {
        log.info("Starting stale bucket cleanup");

        try {
            // Get stale buckets (accumulating for more than configured days)
            int staleDays = 30; // TODO: Make configurable
            List<EdiFileBucket> staleBuckets = bucketRepository.findStaleBuckets(staleDays);

            if (staleBuckets.isEmpty()) {
                log.debug("No stale buckets found");
                return;
            }

            log.warn("Found {} stale buckets (accumulating > {} days)", staleBuckets.size(), staleDays);

            for (EdiFileBucket bucket : staleBuckets) {
                log.warn("Stale bucket detected: bucketId={}, payer={}, payee={}, age={} days, claims={}, amount={}",
                        bucket.getBucketId(),
                        bucket.getPayerName(),
                        bucket.getPayeeName(),
                        bucket.getAgeDays(),
                        bucket.getClaimCount(),
                        bucket.getTotalAmount());

                // TODO: Implement cleanup action (e.g., force transition, send alert, etc.)
                // For now, just log the issue
            }

        } catch (Exception e) {
            log.error("Error during stale bucket cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets monitoring statistics.
     *
     * @return statistics as a formatted string
     */
    public String getMonitoringStatistics() {
        try {
            List<EdiFileBucket> activeBuckets = bucketRepository.findActiveBuckets();
            List<EdiFileBucket> pendingBuckets = bucketRepository.findPendingApprovals();

            return String.format("Active buckets: %d, Pending approval: %d",
                    activeBuckets.size(), pendingBuckets.size());

        } catch (Exception e) {
            log.error("Error getting monitoring statistics: {}", e.getMessage(), e);
            return "Statistics unavailable";
        }
    }

    /**
     * Manually triggers threshold evaluation for a specific bucket.
     * Used for testing or on-demand evaluation.
     *
     * @param bucketId the bucket ID to evaluate
     */
    @Transactional
    public void evaluateBucketById(String bucketId) {
        log.info("Manual threshold evaluation requested for bucket {}", bucketId);

        try {
            EdiFileBucket bucket = bucketRepository.findById(java.util.UUID.fromString(bucketId))
                    .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

            bucketManagerService.evaluateBucketThresholds(bucket);

            log.info("Manual evaluation complete for bucket {}", bucketId);

        } catch (Exception e) {
            log.error("Error evaluating bucket {}: {}", bucketId, e.getMessage(), e);
            throw new RuntimeException("Failed to evaluate bucket: " + e.getMessage(), e);
        }
    }
}

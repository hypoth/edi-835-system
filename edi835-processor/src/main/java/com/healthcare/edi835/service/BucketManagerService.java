package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.EdiCommitCriteria;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.EdiGenerationThreshold;
import com.healthcare.edi835.event.BucketStatusChangeEvent;
import com.healthcare.edi835.repository.EdiCommitCriteriaRepository;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.EdiGenerationThresholdRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing bucket lifecycle and state transitions.
 * Implements the bucket state machine: ACCUMULATING → PENDING_APPROVAL → GENERATING → COMPLETED
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Evaluate bucket thresholds</li>
 *   <li>Manage state transitions</li>
 *   <li>Determine commit mode (AUTO/MANUAL/HYBRID)</li>
 *   <li>Trigger file generation</li>
 * </ul>
 */
@Slf4j
@Service
public class BucketManagerService {

    private final EdiFileBucketRepository bucketRepository;
    private final EdiGenerationThresholdRepository thresholdRepository;
    private final EdiCommitCriteriaRepository commitCriteriaRepository;
    private final CommitCriteriaService commitCriteriaService;
    private final ApplicationEventPublisher eventPublisher;

    public BucketManagerService(
            EdiFileBucketRepository bucketRepository,
            EdiGenerationThresholdRepository thresholdRepository,
            EdiCommitCriteriaRepository commitCriteriaRepository,
            CommitCriteriaService commitCriteriaService,
            ApplicationEventPublisher eventPublisher) {
        this.bucketRepository = bucketRepository;
        this.thresholdRepository = thresholdRepository;
        this.commitCriteriaRepository = commitCriteriaRepository;
        this.commitCriteriaService = commitCriteriaService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Evaluates thresholds for a bucket and triggers appropriate action.
     *
     * @param bucket the bucket to evaluate
     */
    @Transactional
    public void evaluateBucketThresholds(EdiFileBucket bucket) {
        if (bucket.getStatus() != EdiFileBucket.BucketStatus.ACCUMULATING) {
            log.debug("Skipping threshold evaluation for bucket {} in status {}",
                    bucket.getBucketId(), bucket.getStatus());
            return;
        }

        log.debug("Evaluating thresholds for bucket {}: claims={}, amount={}",
                bucket.getBucketId(), bucket.getClaimCount(), bucket.getTotalAmount());

        // Get rule ID from bucket
        UUID ruleId = bucket.getBucketingRule() != null ?
                      bucket.getBucketingRule().getId() : null;

        if (ruleId == null) {
            log.debug("Bucket {} has no bucketing rule assigned", bucket.getBucketId());
            return;
        }

        // Get thresholds for this bucket's rule by ID (avoids entity identity comparison issues)
        List<EdiGenerationThreshold> thresholds = thresholdRepository
                .findByLinkedBucketingRuleIdAndIsActiveTrue(ruleId);

        if (thresholds.isEmpty()) {
            log.debug("No thresholds configured for bucket {} (rule ID: {})",
                    bucket.getBucketId(), ruleId);
            return;
        }

        // Check if any threshold is met
        boolean thresholdMet = false;
        EdiGenerationThreshold metThreshold = null;

        for (EdiGenerationThreshold threshold : thresholds) {
            if (isThresholdMet(bucket, threshold)) {
                thresholdMet = true;
                metThreshold = threshold;
                log.info("Threshold met for bucket {}: type={}, threshold={}",
                        bucket.getBucketId(), threshold.getThresholdType(),
                        threshold.getThresholdName());
                break;
            }
        }

        if (thresholdMet) {
            handleThresholdMet(bucket, metThreshold);
        }
    }

    /**
     * Checks if a threshold is met for a bucket.
     *
     * @param bucket the bucket to check
     * @param threshold the threshold to evaluate
     * @return true if threshold is met
     */
    private boolean isThresholdMet(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        return switch (threshold.getThresholdType()) {
            case CLAIM_COUNT -> isClaimCountThresholdMet(bucket, threshold);
            case AMOUNT -> isAmountThresholdMet(bucket, threshold);
            case TIME -> isTimeThresholdMet(bucket, threshold);
            case HYBRID -> isHybridThresholdMet(bucket, threshold);
        };
    }

    /**
     * Checks if claim count threshold is met.
     */
    private boolean isClaimCountThresholdMet(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        if (threshold.getMaxClaims() == null) {
            return false;
        }

        boolean met = bucket.getClaimCount() >= threshold.getMaxClaims();
        if (met) {
            log.debug("Claim count threshold met: {} >= {}",
                    bucket.getClaimCount(), threshold.getMaxClaims());
        }
        return met;
    }

    /**
     * Checks if amount threshold is met.
     */
    private boolean isAmountThresholdMet(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        if (threshold.getMaxAmount() == null) {
            return false;
        }

        boolean met = bucket.getTotalAmount().compareTo(threshold.getMaxAmount()) >= 0;
        if (met) {
            log.debug("Amount threshold met: {} >= {}",
                    bucket.getTotalAmount(), threshold.getMaxAmount());
        }
        return met;
    }

    /**
     * Checks if time threshold is met.
     */
    private boolean isTimeThresholdMet(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        if (threshold.getTimeDuration() == null) {
            return false;
        }

        LocalDateTime bucketCreated = bucket.getCreatedAt();
        LocalDateTime now = LocalDateTime.now();

        long hoursSinceCreation = ChronoUnit.HOURS.between(bucketCreated, now);

        boolean met = switch (threshold.getTimeDuration()) {
            case DAILY -> hoursSinceCreation >= 24;
            case WEEKLY -> hoursSinceCreation >= 168; // 7 * 24
            case BIWEEKLY -> hoursSinceCreation >= 336; // 14 * 24
            case MONTHLY -> hoursSinceCreation >= 720; // 30 * 24
        };

        if (met) {
            log.debug("Time threshold met: bucket age {} hours, duration: {}",
                    hoursSinceCreation, threshold.getTimeDuration());
        }
        return met;
    }

    /**
     * Checks if hybrid threshold is met (first threshold that triggers wins).
     */
    private boolean isHybridThresholdMet(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        return isClaimCountThresholdMet(bucket, threshold) ||
               isAmountThresholdMet(bucket, threshold) ||
               isTimeThresholdMet(bucket, threshold);
    }

    /**
     * Handles a bucket that has met its threshold.
     *
     * @param bucket the bucket that met threshold
     * @param threshold the threshold that was met
     */
    private void handleThresholdMet(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        // Get commit criteria for this bucket's rule
        List<EdiCommitCriteria> criteriaList = commitCriteriaRepository
                .findByLinkedBucketingRuleAndIsActiveTrue(bucket.getBucketingRule());

        if (criteriaList.isEmpty()) {
            log.warn("No commit criteria found for bucket {}, defaulting to AUTO mode",
                    bucket.getBucketId());
            transitionToGeneration(bucket);
            return;
        }

        // Handle case where multiple active criteria exist (configuration error)
        if (criteriaList.size() > 1) {
            log.warn("Multiple active commit criteria found for bucket {} (rule: {}). Found {} criteria. Using first match: {}",
                    bucket.getBucketId(),
                    bucket.getBucketingRule() != null ? bucket.getBucketingRule().getRuleName() : "NULL",
                    criteriaList.size(),
                    criteriaList.get(0).getCriteriaName());
        }

        EdiCommitCriteria criteria = criteriaList.get(0);

        // Determine action based on commit mode
        switch (criteria.getCommitMode()) {
            case AUTO -> {
                log.info("AUTO-COMMIT mode: transitioning bucket {} to generation",
                        bucket.getBucketId());
                transitionToGeneration(bucket);
            }
            case MANUAL -> {
                log.info("MANUAL-COMMIT mode: transitioning bucket {} to pending approval",
                        bucket.getBucketId());
                transitionToPendingApproval(bucket);
            }
            case HYBRID -> {
                boolean requiresApproval = commitCriteriaService
                        .requiresApproval(bucket, criteria);

                if (requiresApproval) {
                    log.info("HYBRID mode: bucket {} requires approval", bucket.getBucketId());
                    transitionToPendingApproval(bucket);
                } else {
                    log.info("HYBRID mode: bucket {} auto-generating", bucket.getBucketId());
                    transitionToGeneration(bucket);
                }
            }
        }
    }

    /**
     * Transitions bucket to GENERATING status.
     *
     * @param bucket the bucket to transition
     */
    @Transactional
    public void transitionToGeneration(EdiFileBucket bucket) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.info("Transitioning bucket {} from {} to GENERATING",
                bucket.getBucketId(), previousStatus);

        bucket.markGenerating();
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event to trigger EDI file generation
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.GENERATING);
        eventPublisher.publishEvent(event);

        log.debug("Bucket {} ready for file generation. Event published.", bucket.getBucketId());
    }

    /**
     * Transitions bucket to PENDING_APPROVAL status.
     *
     * @param bucket the bucket to transition
     */
    @Transactional
    public void transitionToPendingApproval(EdiFileBucket bucket) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.info("Transitioning bucket {} from {} to PENDING_APPROVAL",
                bucket.getBucketId(), previousStatus);

        bucket.markPendingApproval();
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event for notifications or other workflows
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.PENDING_APPROVAL);
        eventPublisher.publishEvent(event);

        log.info("Bucket {} is now awaiting manual approval", bucket.getBucketId());
    }

    /**
     * Transitions bucket to COMPLETED status.
     *
     * @param bucket the bucket to mark as completed
     */
    @Transactional
    public void markCompleted(EdiFileBucket bucket) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.info("Marking bucket {} as COMPLETED", bucket.getBucketId());

        bucket.markCompleted();
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event for audit or delivery workflows
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.COMPLETED);
        eventPublisher.publishEvent(event);
    }

    /**
     * Transitions bucket to FAILED status.
     *
     * @param bucket the bucket to mark as failed
     */
    @Transactional
    public void markFailed(EdiFileBucket bucket) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.error("Marking bucket {} as FAILED", bucket.getBucketId());

        bucket.markFailed();
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event for alerts or retry workflows
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.FAILED);
        eventPublisher.publishEvent(event);
    }

    /**
     * Transitions bucket to MISSING_CONFIGURATION status.
     *
     * @param bucket the bucket to mark as missing configuration
     */
    @Transactional
    public void markMissingConfiguration(EdiFileBucket bucket) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.warn("Marking bucket {} as MISSING_CONFIGURATION. User action required.", bucket.getBucketId());

        bucket.markMissingConfiguration();
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event for UI notification
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.MISSING_CONFIGURATION);
        eventPublisher.publishEvent(event);
    }

    /**
     * Gets all active buckets.
     *
     * @return list of active buckets
     */
    public List<EdiFileBucket> getActiveBuckets() {
        return bucketRepository.findActiveBuckets();
    }

    /**
     * Gets all buckets pending approval.
     *
     * @return list of buckets pending approval
     */
    public List<EdiFileBucket> getPendingApprovals() {
        return bucketRepository.findPendingApprovals();
    }
}

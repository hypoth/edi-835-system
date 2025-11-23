package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.CheckPaymentWorkflowConfig;
import com.healthcare.edi835.entity.EdiCommitCriteria;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.EdiGenerationThreshold;
import com.healthcare.edi835.event.BucketStatusChangeEvent;
import com.healthcare.edi835.exception.CheckAssignmentException;
import com.healthcare.edi835.exception.PaymentRequiredException;
import com.healthcare.edi835.repository.CheckPaymentConfigRepository;
import com.healthcare.edi835.repository.CheckPaymentWorkflowConfigRepository;
import com.healthcare.edi835.repository.EdiCommitCriteriaRepository;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.EdiGenerationThresholdRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
    private final CheckPaymentConfigRepository configRepository;
    private final CheckPaymentWorkflowConfigRepository workflowConfigRepository;
    private final CheckPaymentService checkPaymentService;
    private final ApplicationEventPublisher eventPublisher;

    public BucketManagerService(
            EdiFileBucketRepository bucketRepository,
            EdiGenerationThresholdRepository thresholdRepository,
            EdiCommitCriteriaRepository commitCriteriaRepository,
            CommitCriteriaService commitCriteriaService,
            CheckPaymentConfigRepository configRepository,
            CheckPaymentWorkflowConfigRepository workflowConfigRepository,
            @Lazy CheckPaymentService checkPaymentService,
            ApplicationEventPublisher eventPublisher) {
        this.bucketRepository = bucketRepository;
        this.thresholdRepository = thresholdRepository;
        this.commitCriteriaRepository = commitCriteriaRepository;
        this.commitCriteriaService = commitCriteriaService;
        this.configRepository = configRepository;
        this.workflowConfigRepository = workflowConfigRepository;
        this.checkPaymentService = checkPaymentService;
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
            handleAutoCommitWithPayment(bucket, threshold);
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
                log.info("AUTO-COMMIT mode: processing bucket {} for generation",
                        bucket.getBucketId());
                handleAutoCommitWithPayment(bucket, threshold);
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
                    handleAutoCommitWithPayment(bucket, threshold);
                }
            }
        }
    }

    /**
     * Handles AUTO-COMMIT mode with payment auto-assignment support.
     *
     * <p>If the bucket requires payment and workflow is configured for SEPARATE mode
     * with AUTO assignment, this method will automatically assign a check before
     * transitioning to generation.</p>
     *
     * @param bucket the bucket to process
     * @param threshold the threshold that was met (used to look up workflow config)
     * @throws CheckAssignmentException if auto-assignment is configured but fails
     * @throws PaymentRequiredException if payment is required but cannot be auto-assigned
     */
    private void handleAutoCommitWithPayment(EdiFileBucket bucket, EdiGenerationThreshold threshold) {
        // Check if payment is required
        if (!bucket.isPaymentRequired()) {
            log.debug("Payment not required for bucket {}, proceeding to generation", bucket.getBucketId());
            transitionToGeneration(bucket);
            return;
        }

        // Check if payment is already assigned
        if (bucket.hasPaymentAssigned()) {
            log.debug("Payment already assigned for bucket {}, proceeding to generation", bucket.getBucketId());
            transitionToGeneration(bucket);
            return;
        }

        // Payment required but not assigned - check if auto-assignment is configured
        log.debug("Bucket {} requires payment, checking for auto-assignment configuration", bucket.getBucketId());

        // Look up workflow config for this threshold
        Optional<CheckPaymentWorkflowConfig> workflowConfigOpt = workflowConfigRepository
                .findByThresholdId(threshold.getId());

        if (workflowConfigOpt.isEmpty()) {
            log.warn("No workflow config found for threshold {}, cannot auto-assign payment for bucket {}",
                    threshold.getId(), bucket.getBucketId());
            throw new PaymentRequiredException(bucket.getBucketId(),
                    "Payment required but no workflow configuration found for auto-assignment. " +
                    "Please configure Check Payment Workflow or assign payment manually.");
        }

        CheckPaymentWorkflowConfig workflowConfig = workflowConfigOpt.get();

        // Check if workflow is SEPARATE with AUTO assignment
        if (workflowConfig.isSeparateWorkflow() && workflowConfig.allowsAutoAssignment()) {
            log.info("Bucket {} has SEPARATE workflow with AUTO assignment mode, attempting auto-assignment",
                    bucket.getBucketId());

            try {
                // Auto-assign check
                checkPaymentService.assignCheckAutomaticallyFromBucket(bucket.getBucketId(), "SYSTEM");
                log.info("Successfully auto-assigned check to bucket {} in AUTO-COMMIT mode", bucket.getBucketId());

                // Reload bucket to get updated state with assigned payment
                EdiFileBucket updatedBucket = bucketRepository.findById(bucket.getBucketId())
                        .orElseThrow(() -> new IllegalStateException("Bucket not found after assignment: " + bucket.getBucketId()));

                // Now transition to generation (payment is assigned)
                transitionToGeneration(updatedBucket);

            } catch (Exception e) {
                // Wrap and re-throw to provide clear error message
                log.error("Auto-assignment failed for bucket {} in AUTO-COMMIT mode: {}",
                        bucket.getBucketId(), e.getMessage(), e);
                throw new CheckAssignmentException(bucket.getBucketId(), "AUTO", e);
            }
        } else {
            // Workflow not configured for auto-assignment
            log.warn("Bucket {} requires payment but workflow mode is {} with assignment mode {}. " +
                     "Auto-assignment not applicable in AUTO-COMMIT mode.",
                    bucket.getBucketId(), workflowConfig.getWorkflowMode(), workflowConfig.getAssignmentMode());

            throw new PaymentRequiredException(bucket.getBucketId(),
                    String.format("Payment required but workflow is configured as %s/%s. " +
                            "For AUTO-COMMIT with payment, configure workflow as SEPARATE/AUTO or assign payment manually.",
                            workflowConfig.getWorkflowMode(), workflowConfig.getAssignmentMode()));
        }
    }

    /**
     * Transitions bucket to GENERATING status.
     * Validates that payment is assigned if required before allowing transition.
     *
     * @param bucket the bucket to transition
     * @throws PaymentRequiredException if payment is required but not ready
     */
    @Transactional
    public void transitionToGeneration(EdiFileBucket bucket) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.info("Transitioning bucket {} from {} to GENERATING",
                bucket.getBucketId(), previousStatus);

        // Validate payment readiness (Phase 1: Check Payment Implementation)
        validatePaymentReadiness(bucket);

        bucket.markGenerating();
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event to trigger EDI file generation
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.GENERATING);
        eventPublisher.publishEvent(event);

        log.debug("Bucket {} ready for file generation. Event published.", bucket.getBucketId());
    }

    /**
     * Validates that payment is ready for EDI generation if required.
     *
     * @param bucket the bucket to validate
     * @throws PaymentRequiredException if payment validation fails
     */
    private void validatePaymentReadiness(EdiFileBucket bucket) {
        // Skip validation if payment not required
        if (!bucket.isPaymentRequired()) {
            log.debug("Payment not required for bucket {}", bucket.getBucketId());
            return;
        }

        // Check if payment is assigned
        if (!bucket.hasPaymentAssigned()) {
            throw new PaymentRequiredException(bucket.getBucketId(),
                    "Payment must be assigned before EDI generation");
        }

        // Check if payment meets acknowledgment requirements (configurable)
        boolean requireAcknowledgment = configRepository.requireAcknowledgmentBeforeEdi();
        if (!bucket.isPaymentReadyForEdi(requireAcknowledgment)) {
            String message = requireAcknowledgment ?
                    "Payment must be acknowledged before EDI generation" :
                    "Payment must be assigned before EDI generation";
            throw new PaymentRequiredException(bucket.getBucketId(), message);
        }

        log.debug("Payment validation passed for bucket {}: status={}",
                bucket.getBucketId(), bucket.getPaymentStatus());
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
        markFailed(bucket, (String) null);
    }

    /**
     * Transitions bucket to FAILED status with an error message.
     *
     * @param bucket the bucket to mark as failed
     * @param errorMessage the error message describing the failure
     */
    @Transactional
    public void markFailed(EdiFileBucket bucket, String errorMessage) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.error("Marking bucket {} as FAILED. Reason: {}", bucket.getBucketId(),
                errorMessage != null ? errorMessage : "No reason provided");

        if (errorMessage != null) {
            bucket.markFailed(errorMessage);
        } else {
            bucket.markFailed();
        }
        EdiFileBucket savedBucket = bucketRepository.save(bucket);

        // Publish event for alerts or retry workflows
        BucketStatusChangeEvent event = new BucketStatusChangeEvent(
                this, savedBucket, previousStatus, EdiFileBucket.BucketStatus.FAILED);
        eventPublisher.publishEvent(event);
    }

    /**
     * Transitions bucket to FAILED status with exception details.
     *
     * @param bucket the bucket to mark as failed
     * @param exception the exception that caused the failure
     */
    @Transactional
    public void markFailed(EdiFileBucket bucket, Throwable exception) {
        EdiFileBucket.BucketStatus previousStatus = bucket.getStatus();

        log.error("Marking bucket {} as FAILED due to exception: {}",
                bucket.getBucketId(), exception.getMessage(), exception);

        bucket.markFailed(exception);
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

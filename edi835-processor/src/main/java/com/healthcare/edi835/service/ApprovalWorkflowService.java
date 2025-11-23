package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.BucketApprovalLog;
import com.healthcare.edi835.entity.CheckPaymentWorkflowConfig;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.EdiGenerationThreshold;
import com.healthcare.edi835.exception.CheckAssignmentException;
import com.healthcare.edi835.repository.BucketApprovalLogRepository;
import com.healthcare.edi835.repository.CheckPaymentWorkflowConfigRepository;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.EdiGenerationThresholdRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing bucket approval workflow.
 * Handles approval queue, approve/reject operations, and approval logging.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Approve buckets for generation</li>
 *   <li>Reject buckets with reasons</li>
 *   <li>Log all approval actions</li>
 *   <li>Manage approval queue</li>
 *   <li>Validate approval permissions</li>
 * </ul>
 */
@Slf4j
@Service
public class ApprovalWorkflowService {

    private final EdiFileBucketRepository bucketRepository;
    private final BucketApprovalLogRepository approvalLogRepository;
    private final BucketManagerService bucketManagerService;
    private final EdiGenerationThresholdRepository thresholdRepository;
    private final CheckPaymentWorkflowConfigRepository workflowConfigRepository;
    private final CheckPaymentService checkPaymentService;

    public ApprovalWorkflowService(
            EdiFileBucketRepository bucketRepository,
            BucketApprovalLogRepository approvalLogRepository,
            BucketManagerService bucketManagerService,
            EdiGenerationThresholdRepository thresholdRepository,
            CheckPaymentWorkflowConfigRepository workflowConfigRepository,
            CheckPaymentService checkPaymentService) {
        this.bucketRepository = bucketRepository;
        this.approvalLogRepository = approvalLogRepository;
        this.bucketManagerService = bucketManagerService;
        this.thresholdRepository = thresholdRepository;
        this.workflowConfigRepository = workflowConfigRepository;
        this.checkPaymentService = checkPaymentService;
    }

    /**
     * Approves a bucket for EDI file generation.
     * Note: This marks the bucket as approved but does NOT trigger generation.
     * Generation is triggered separately after payment assignment (if required).
     *
     * @param bucketId the bucket ID to approve
     * @param approvedBy the username/ID of the approver
     * @param comments optional approval comments
     * @throws IllegalStateException if bucket is not in PENDING_APPROVAL status
     */
    @Transactional
    public void approveBucket(UUID bucketId, String approvedBy, String comments) {
        log.info("Approval requested for bucket {} by {}", bucketId, approvedBy);

        // Get the bucket
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        // Validate bucket status
        if (bucket.getStatus() != EdiFileBucket.BucketStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    String.format("Bucket %s is not pending approval. Current status: %s",
                            bucketId, bucket.getStatus()));
        }

        // Log approval
        BucketApprovalLog approvalLog = BucketApprovalLog.forApproval(
                bucket, approvedBy, comments);
        approvalLogRepository.save(approvalLog);

        // Mark bucket as approved (set approval metadata)
        // Status remains PENDING_APPROVAL until payment is assigned (if required)
        bucket.setApprovedBy(approvedBy);
        bucket.setApprovedAt(java.time.LocalDateTime.now());

        // If payment is NOT required, trigger generation immediately
        if (!bucket.isPaymentRequired()) {
            log.info("Payment not required for bucket {}, transitioning to generation", bucketId);
            bucketManagerService.transitionToGeneration(bucket);
            log.info("Bucket {} approved by {} and transitioned to GENERATING. Claims: {}, Amount: {}",
                    bucketId, approvedBy, bucket.getClaimCount(), bucket.getTotalAmount());
        } else {
            // Payment required - check if auto-assignment is configured
            boolean autoAssigned = attemptAutoAssignment(bucket, approvedBy);

            if (autoAssigned) {
                // Auto-assignment succeeded - CheckPaymentService already triggered generation
                // Just log success (don't call transitionToGeneration again)
                log.info("Bucket {} approved by {} with auto-assigned check and transitioned to GENERATING. Claims: {}, Amount: {}",
                        bucketId, approvedBy, bucket.getClaimCount(), bucket.getTotalAmount());
            } else {
                // Auto-assignment not configured or failed - save and await manual assignment
                bucketRepository.save(bucket);
                log.info("Bucket {} approved by {} and awaiting manual check payment assignment. Claims: {}, Amount: {}",
                        bucketId, approvedBy, bucket.getClaimCount(), bucket.getTotalAmount());
            }
        }
    }

    /**
     * Attempts to auto-assign a check to the bucket if workflow is configured for it.
     *
     * <p>This method propagates exceptions to ensure the entire approval transaction
     * is rolled back if check assignment fails. This guarantees atomicity - either
     * approval + check assignment both succeed, or both are rolled back.</p>
     *
     * @param bucket the bucket to assign check to
     * @param approvedBy the user who approved the bucket
     * @return true if check was auto-assigned, false if auto-assignment is not configured
     * @throws CheckAssignmentException if auto-assignment is configured but fails
     */
    private boolean attemptAutoAssignment(EdiFileBucket bucket, String approvedBy) {
        // Get bucketing rule ID from bucket
        UUID bucketingRuleId = bucket.getBucketingRuleId();
        if (bucketingRuleId == null) {
            log.debug("Bucket {} has no bucketing rule, skipping auto-assignment", bucket.getBucketId());
            return false;
        }

        // Look up threshold for this bucketing rule
        Optional<EdiGenerationThreshold> thresholdOpt = thresholdRepository
                .findByLinkedBucketingRuleId(bucketingRuleId);
        if (thresholdOpt.isEmpty()) {
            log.debug("No threshold found for bucketing rule {}, skipping auto-assignment", bucketingRuleId);
            return false;
        }

        // Look up workflow config for this threshold
        Optional<CheckPaymentWorkflowConfig> workflowConfigOpt = workflowConfigRepository
                .findByThresholdId(thresholdOpt.get().getId());
        if (workflowConfigOpt.isEmpty()) {
            log.debug("No workflow config found for threshold {}, skipping auto-assignment",
                    thresholdOpt.get().getId());
            return false;
        }

        CheckPaymentWorkflowConfig workflowConfig = workflowConfigOpt.get();

        // Check if workflow is SEPARATE and allows auto-assignment
        if (workflowConfig.isSeparateWorkflow() && workflowConfig.allowsAutoAssignment()) {
            log.info("Bucket {} has SEPARATE workflow with AUTO assignment mode, attempting auto-assignment",
                    bucket.getBucketId());

            try {
                // Auto-assign check - let exceptions propagate to trigger rollback
                checkPaymentService.assignCheckAutomaticallyFromBucket(bucket.getBucketId(), approvedBy);
                log.info("Successfully auto-assigned check to bucket {}", bucket.getBucketId());

                // Reload bucket to get updated state (check payment assigned)
                EdiFileBucket updatedBucket = bucketRepository.findById(bucket.getBucketId())
                        .orElse(bucket);
                bucket.setCheckPayment(updatedBucket.getCheckPayment());
                bucket.setPaymentStatus(updatedBucket.getPaymentStatus());
                bucket.setPaymentDate(updatedBucket.getPaymentDate());

                return true;
            } catch (Exception e) {
                // Wrap and re-throw to trigger transaction rollback
                log.error("Auto-assignment failed for bucket {}. Rolling back approval transaction.",
                        bucket.getBucketId(), e);
                throw new CheckAssignmentException(bucket.getBucketId(), "AUTO", e);
            }
        } else {
            log.debug("Bucket {} workflow mode is {} with assignment mode {}, auto-assignment not applicable",
                    bucket.getBucketId(), workflowConfig.getWorkflowMode(), workflowConfig.getAssignmentMode());
            return false;
        }
    }

    /**
     * Rejects a bucket and prevents EDI file generation.
     *
     * @param bucketId the bucket ID to reject
     * @param rejectedBy the username/ID of the rejector
     * @param rejectionReason the reason for rejection (required)
     * @throws IllegalStateException if bucket is not in PENDING_APPROVAL status
     * @throws IllegalArgumentException if rejectionReason is null or empty
     */
    @Transactional
    public void rejectBucket(UUID bucketId, String rejectedBy, String rejectionReason) {
        log.info("Rejection requested for bucket {} by {}", bucketId, rejectedBy);

        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        // Get the bucket
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        // Validate bucket status
        if (bucket.getStatus() != EdiFileBucket.BucketStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    String.format("Bucket %s is not pending approval. Current status: %s",
                            bucketId, bucket.getStatus()));
        }

        // Log rejection
        BucketApprovalLog rejectionLog = BucketApprovalLog.forRejection(
                bucket, rejectedBy, rejectionReason);
        approvalLogRepository.save(rejectionLog);

        // Transition bucket to FAILED with rejection reason
        String errorMessage = String.format("Rejected by %s: %s", rejectedBy, rejectionReason);
        bucket.markFailed(errorMessage);
        bucketRepository.save(bucket);

        log.warn("Bucket {} rejected by {}. Reason: {}", bucketId, rejectedBy, rejectionReason);
    }

    /**
     * Gets all buckets pending approval.
     *
     * @return list of buckets pending approval
     */
    public List<EdiFileBucket> getPendingApprovals() {
        log.debug("Retrieving all buckets pending approval");
        return bucketRepository.findPendingApprovals();
    }

    /**
     * Gets buckets pending approval for a specific payer.
     *
     * @param payerId the payer ID
     * @return list of buckets pending approval for the payer
     */
    public List<EdiFileBucket> getPendingApprovalsForPayer(String payerId) {
        log.debug("Retrieving pending approvals for payer: {}", payerId);
        return bucketRepository.findByPayerIdAndStatus(payerId, EdiFileBucket.BucketStatus.PENDING_APPROVAL);
    }

    /**
     * Gets buckets pending approval for a specific payee.
     *
     * @param payeeId the payee ID
     * @return list of buckets pending approval for the payee
     */
    public List<EdiFileBucket> getPendingApprovalsForPayee(String payeeId) {
        log.debug("Retrieving pending approvals for payee: {}", payeeId);
        return bucketRepository.findByPayeeIdAndStatus(payeeId, EdiFileBucket.BucketStatus.PENDING_APPROVAL);
    }

    /**
     * Gets approval history for a bucket.
     *
     * @param bucketId the bucket ID
     * @return list of approval log entries
     */
    public List<BucketApprovalLog> getApprovalHistory(UUID bucketId) {
        log.debug("Retrieving approval history for bucket: {}", bucketId);

        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        return approvalLogRepository.findByBucket(bucket);
    }

    /**
     * Gets all approvals made by a specific user.
     *
     * @param username the username/ID
     * @return list of approval log entries
     */
    public List<BucketApprovalLog> getApprovalsByUser(String username) {
        log.debug("Retrieving approvals by user: {}", username);
        return approvalLogRepository.findByApprovedBy(username);
    }

    /**
     * Gets approval statistics.
     *
     * @return statistics as object array
     */
    public List<Object[]> getApprovalStatistics() {
        log.debug("Retrieving approval statistics");
        return approvalLogRepository.getApprovalStatistics();
    }

    /**
     * Checks if a user is authorized to approve a bucket.
     * This is a placeholder for role-based authorization logic.
     *
     * @param bucketId the bucket ID
     * @param username the username/ID
     * @param userRoles the user's roles (comma-separated)
     * @return true if user is authorized
     */
    public boolean isAuthorizedToApprove(UUID bucketId, String username, String userRoles) {
        // TODO: Implement role-based authorization
        // This should check:
        // 1. User has appropriate role (from EdiCommitCriteria.approvalRoles)
        // 2. User is not the same as bucket creator
        // 3. Any organizational rules (e.g., payer-specific approvers)

        log.debug("Checking authorization for user {} to approve bucket {}", username, bucketId);

        if (userRoles == null || userRoles.trim().isEmpty()) {
            log.warn("User {} has no roles assigned", username);
            return false;
        }

        // For now, return true if user has any of the standard approval roles
        String[] roles = userRoles.split(",");
        for (String role : roles) {
            String trimmedRole = role.trim().toUpperCase();
            if (trimmedRole.contains("ADMIN") ||
                trimmedRole.contains("MANAGER") ||
                trimmedRole.contains("APPROVER")) {
                return true;
            }
        }

        log.warn("User {} does not have approval permissions", username);
        return false;
    }

    /**
     * Bulk approves multiple buckets.
     * Note: This method is NOT transactional - each bucket approval runs in its own transaction.
     * This allows partial success: some buckets can succeed even if others fail.
     *
     * @param bucketIds list of bucket IDs to approve
     * @param approvedBy the username/ID of the approver
     * @param comments optional approval comments
     * @return count of successfully approved buckets
     */
    public int bulkApproveBuckets(List<UUID> bucketIds, String approvedBy, String comments) {
        log.info("Bulk approval requested for {} buckets by {}", bucketIds.size(), approvedBy);

        int approvedCount = 0;
        int errorCount = 0;

        for (UUID bucketId : bucketIds) {
            try {
                approveBucket(bucketId, approvedBy, comments);
                approvedCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to approve bucket {} in bulk operation: {}",
                        bucketId, e.getMessage(), e);
            }
        }

        log.info("Bulk approval complete: total={}, approved={}, errors={}",
                bucketIds.size(), approvedCount, errorCount);

        return approvedCount;
    }

    /**
     * Resets a failed bucket back to accumulating status.
     * Used when a rejection was made in error.
     *
     * @param bucketId the bucket ID to reset
     * @param resetBy the username/ID performing the reset
     * @param reason the reason for reset
     */
    @Transactional
    public void resetFailedBucket(UUID bucketId, String resetBy, String reason) {
        log.info("Reset requested for bucket {} by {}", bucketId, resetBy);

        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        if (bucket.getStatus() != EdiFileBucket.BucketStatus.FAILED) {
            throw new IllegalStateException(
                    String.format("Only FAILED buckets can be reset. Current status: %s",
                            bucket.getStatus()));
        }

        // Log the reset action
        BucketApprovalLog resetLog = BucketApprovalLog.builder()
                .bucket(bucket)
                .action(BucketApprovalLog.ApprovalAction.OVERRIDE)
                .approvedBy(resetBy)
                .comments("RESET: " + reason)
                .build();
        approvalLogRepository.save(resetLog);

        // Reset bucket to accumulating
        bucket.setStatus(EdiFileBucket.BucketStatus.ACCUMULATING);
        bucket.setAwaitingApprovalSince(null);
        bucketRepository.save(bucket);

        log.info("Bucket {} reset to ACCUMULATING by {}. Reason: {}", bucketId, resetBy, reason);
    }
}

package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.BucketApprovalLog;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.repository.BucketApprovalLogRepository;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public ApprovalWorkflowService(
            EdiFileBucketRepository bucketRepository,
            BucketApprovalLogRepository approvalLogRepository,
            BucketManagerService bucketManagerService) {
        this.bucketRepository = bucketRepository;
        this.approvalLogRepository = approvalLogRepository;
        this.bucketManagerService = bucketManagerService;
    }

    /**
     * Approves a bucket for EDI file generation.
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

        // Transition bucket to generation
        bucketManagerService.transitionToGeneration(bucket);

        log.info("Bucket {} approved by {} and transitioned to GENERATING. Claims: {}, Amount: {}",
                bucketId, approvedBy, bucket.getClaimCount(), bucket.getTotalAmount());
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

        // Transition bucket back to ACCUMULATING or mark as FAILED
        bucket.markFailed();
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
     *
     * @param bucketIds list of bucket IDs to approve
     * @param approvedBy the username/ID of the approver
     * @param comments optional approval comments
     * @return count of successfully approved buckets
     */
    @Transactional
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

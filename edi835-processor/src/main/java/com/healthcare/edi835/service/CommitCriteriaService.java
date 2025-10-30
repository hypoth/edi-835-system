package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.EdiCommitCriteria;
import com.healthcare.edi835.entity.EdiFileBucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service for evaluating commit criteria and determining approval requirements.
 * Implements AUTO, MANUAL, and HYBRID commit mode logic.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Evaluate commit mode for buckets</li>
 *   <li>Determine if bucket requires approval</li>
 *   <li>Apply HYBRID mode thresholds</li>
 * </ul>
 */
@Slf4j
@Service
public class CommitCriteriaService {

    /**
     * Determines if a bucket requires approval based on commit criteria.
     *
     * @param bucket the bucket to evaluate
     * @param criteria the commit criteria to apply
     * @return true if approval is required, false if auto-generation is allowed
     */
    public boolean requiresApproval(EdiFileBucket bucket, EdiCommitCriteria criteria) {
        if (criteria == null) {
            log.warn("No commit criteria provided, defaulting to AUTO mode (no approval required)");
            return false;
        }

        return switch (criteria.getCommitMode()) {
            case AUTO -> {
                log.debug("AUTO mode: bucket {} does not require approval", bucket.getBucketId());
                yield false;
            }
            case MANUAL -> {
                log.debug("MANUAL mode: bucket {} requires approval", bucket.getBucketId());
                yield true;
            }
            case HYBRID -> evaluateHybridMode(bucket, criteria);
        };
    }

    /**
     * Evaluates HYBRID mode commit criteria.
     * In HYBRID mode, buckets require approval if they exceed specified thresholds.
     *
     * @param bucket the bucket to evaluate
     * @param criteria the hybrid commit criteria
     * @return true if approval is required
     */
    private boolean evaluateHybridMode(EdiFileBucket bucket, EdiCommitCriteria criteria) {
        log.debug("Evaluating HYBRID mode for bucket {}: claims={}, amount={}",
                bucket.getBucketId(), bucket.getClaimCount(), bucket.getTotalAmount());

        boolean requiresApproval = false;
        String reason = null;

        // Check claim count threshold
        if (criteria.getApprovalClaimCountThreshold() != null &&
            bucket.getClaimCount() >= criteria.getApprovalClaimCountThreshold()) {
            requiresApproval = true;
            reason = String.format("Claim count %d exceeds threshold %d",
                    bucket.getClaimCount(), criteria.getApprovalClaimCountThreshold());
        }

        // Check amount threshold
        if (criteria.getApprovalAmountThreshold() != null &&
            bucket.getTotalAmount().compareTo(criteria.getApprovalAmountThreshold()) >= 0) {
            requiresApproval = true;
            reason = String.format("Total amount %s exceeds threshold %s",
                    bucket.getTotalAmount(), criteria.getApprovalAmountThreshold());
        }

        // Check approval roles
        if (criteria.getApprovalRoles() != null && !criteria.getApprovalRoles().isEmpty()) {
            log.debug("Approval roles configured: {}", criteria.getApprovalRoles());
            // If roles are configured, approval is required
            requiresApproval = true;
            if (reason == null) {
                reason = "Approval roles configured for this bucketing rule";
            }
        }

        if (requiresApproval) {
            log.info("HYBRID mode: bucket {} requires approval. Reason: {}",
                    bucket.getBucketId(), reason);
        } else {
            log.info("HYBRID mode: bucket {} does not require approval, will auto-generate",
                    bucket.getBucketId());
        }

        return requiresApproval;
    }

    /**
     * Checks if a bucket exceeds claim count threshold.
     *
     * @param bucket the bucket to check
     * @param criteria the commit criteria
     * @return true if threshold is exceeded
     */
    public boolean exceedsClaimCountThreshold(EdiFileBucket bucket, EdiCommitCriteria criteria) {
        if (criteria == null || criteria.getApprovalClaimCountThreshold() == null) {
            return false;
        }

        return bucket.getClaimCount() >= criteria.getApprovalClaimCountThreshold();
    }

    /**
     * Checks if a bucket exceeds amount threshold.
     *
     * @param bucket the bucket to check
     * @param criteria the commit criteria
     * @return true if threshold is exceeded
     */
    public boolean exceedsAmountThreshold(EdiFileBucket bucket, EdiCommitCriteria criteria) {
        if (criteria == null || criteria.getApprovalAmountThreshold() == null) {
            return false;
        }

        return bucket.getTotalAmount().compareTo(criteria.getApprovalAmountThreshold()) >= 0;
    }

    /**
     * Gets the approval roles required for a bucket.
     *
     * @param criteria the commit criteria
     * @return comma-separated list of roles, or null if no roles configured
     */
    public String getRequiredApprovalRoles(EdiCommitCriteria criteria) {
        if (criteria == null || criteria.getApprovalRoles() == null || criteria.getApprovalRoles().isEmpty()) {
            return null;
        }

        return criteria.getApprovalRoles();
    }

    /**
     * Validates commit criteria configuration.
     *
     * @param criteria the commit criteria to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCommitCriteria(EdiCommitCriteria criteria) {
        if (criteria == null) {
            log.warn("Commit criteria is null");
            return false;
        }

        if (criteria.getCommitMode() == null) {
            log.warn("Commit mode is not set");
            return false;
        }

        // Validate HYBRID mode configuration
        if (criteria.getCommitMode() == EdiCommitCriteria.CommitMode.HYBRID) {
            if (criteria.getApprovalClaimCountThreshold() == null &&
                criteria.getApprovalAmountThreshold() == null &&
                (criteria.getApprovalRoles() == null || criteria.getApprovalRoles().isEmpty())) {
                log.warn("HYBRID mode requires at least one threshold or approval roles");
                return false;
            }
        }

        return true;
    }

    /**
     * Gets a human-readable description of commit criteria.
     *
     * @param criteria the commit criteria
     * @return description string
     */
    public String describeCommitCriteria(EdiCommitCriteria criteria) {
        if (criteria == null) {
            return "No commit criteria configured (defaults to AUTO)";
        }

        return switch (criteria.getCommitMode()) {
            case AUTO -> "AUTO: All buckets auto-generate without approval";
            case MANUAL -> "MANUAL: All buckets require approval before generation";
            case HYBRID -> describeHybridMode(criteria);
        };
    }

    /**
     * Describes HYBRID mode configuration.
     *
     * @param criteria the hybrid commit criteria
     * @return description string
     */
    private String describeHybridMode(EdiCommitCriteria criteria) {
        StringBuilder sb = new StringBuilder("HYBRID: Approval required if ");
        boolean hasCondition = false;

        if (criteria.getApprovalClaimCountThreshold() != null) {
            sb.append("claim count >= ").append(criteria.getApprovalClaimCountThreshold());
            hasCondition = true;
        }

        if (criteria.getApprovalAmountThreshold() != null) {
            if (hasCondition) {
                sb.append(" OR ");
            }
            sb.append("total amount >= $").append(criteria.getApprovalAmountThreshold());
            hasCondition = true;
        }

        if (criteria.getApprovalRoles() != null && !criteria.getApprovalRoles().isEmpty()) {
            if (hasCondition) {
                sb.append(" OR ");
            }
            sb.append("approval roles required: ").append(criteria.getApprovalRoles());
        }

        return sb.toString();
    }
}

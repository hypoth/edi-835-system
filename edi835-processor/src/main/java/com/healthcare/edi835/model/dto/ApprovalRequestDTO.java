package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for bucket approval/rejection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequestDTO {

    private String bucketId;
    private String action;  // APPROVE, REJECT, OVERRIDE
    private String approvedBy;
    private String comments;
    private LocalDateTime scheduledGenerationTime;  // Optional: for scheduled generation

    // Override information (for HYBRID mode)
    private Boolean overrideThreshold;
    private String overrideReason;

    // Rejection reason (used when action is REJECT)
    private String rejectionReason;

    // Wrapper methods for backward compatibility
    public String getActionBy() {
        return approvedBy;
    }

    public String getRejectionReason() {
        return rejectionReason != null ? rejectionReason : comments;
    }
}

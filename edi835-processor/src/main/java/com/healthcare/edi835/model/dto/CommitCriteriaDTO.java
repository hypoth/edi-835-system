package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for EDI Commit Criteria.
 * Used to transfer commit criteria data without exposing JPA entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitCriteriaDTO {

    private UUID id;
    private String criteriaName;
    private String commitMode;
    private Integer autoCommitThreshold;
    private Integer manualApprovalThreshold;

    // Frontend-expected field names (aliases for backward compatibility)
    private Integer approvalClaimCountThreshold;  // Alias for manualApprovalThreshold
    private Double approvalAmountThreshold;       // Alias for autoCommitThreshold

    // Convert String[] to List<String> for proper JSON serialization
    private List<String> approvalRequiredRoles;
    private List<String> overridePermissions;

    // Instead of full entity object, just include ID
    private UUID linkedBucketingRuleId;
    private String linkedBucketingRuleName;

    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}

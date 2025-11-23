package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for returning bucket configuration details.
 * Includes threshold, workflow config, and commit criteria.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketConfigurationDTO {

    private ThresholdDTO threshold;
    private WorkflowConfigDTO workflowConfig;
    private CommitCriteriaDTO commitCriteria;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThresholdDTO {
        private String thresholdId;
        private String thresholdName;
        private String thresholdType;
        private Integer maxClaims;
        private BigDecimal maxAmount;
        private String timeDuration;
        private boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowConfigDTO {
        private String id;
        private String configName;
        private String workflowMode;
        private String assignmentMode;
        private boolean requireAcknowledgment;
        private String description;
        private boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitCriteriaDTO {
        private String id;
        private String criteriaName;
        private String commitMode;
        private Integer autoCommitThreshold;
        private Integer manualApprovalThreshold;
        private List<String> approvalRequiredRoles;
        private boolean isActive;
    }
}

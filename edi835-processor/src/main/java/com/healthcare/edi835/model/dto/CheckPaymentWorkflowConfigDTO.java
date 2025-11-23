package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Check Payment Workflow Configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPaymentWorkflowConfigDTO {

    private String id;
    private String configName;
    private String workflowMode;  // NONE, SEPARATE, COMBINED
    private String assignmentMode;  // MANUAL, AUTO, BOTH
    private Boolean requireAcknowledgment;
    private String linkedThresholdId;
    private String linkedThresholdName;  // Denormalized for display
    private String linkedBucketingRuleName;  // Denormalized for display
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    /**
     * Checks if this configuration requires check payment.
     *
     * @return true if workflow mode is not NONE
     */
    public boolean requiresCheckPayment() {
        return !"NONE".equals(workflowMode);
    }

    /**
     * Checks if workflow uses combined approval + assignment dialog.
     *
     * @return true if workflow mode is COMBINED
     */
    public boolean isCombinedWorkflow() {
        return "COMBINED".equals(workflowMode);
    }

    /**
     * Checks if workflow uses separate approval and assignment steps.
     *
     * @return true if workflow mode is SEPARATE
     */
    public boolean isSeparateWorkflow() {
        return "SEPARATE".equals(workflowMode);
    }

    /**
     * Checks if manual assignment is allowed.
     *
     * @return true if assignment mode is MANUAL or BOTH
     */
    public boolean allowsManualAssignment() {
        return "MANUAL".equals(assignmentMode) || "BOTH".equals(assignmentMode);
    }

    /**
     * Checks if automatic assignment is allowed.
     *
     * @return true if assignment mode is AUTO or BOTH
     */
    public boolean allowsAutoAssignment() {
        return "AUTO".equals(assignmentMode) || "BOTH".equals(assignmentMode);
    }
}

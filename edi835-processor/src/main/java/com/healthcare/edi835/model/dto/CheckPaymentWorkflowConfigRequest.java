package com.healthcare.edi835.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating/updating Check Payment Workflow Configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPaymentWorkflowConfigRequest {

    @NotBlank(message = "Configuration name is required")
    private String configName;

    @NotNull(message = "Workflow mode is required")
    @Pattern(regexp = "NONE|SEPARATE|COMBINED", message = "Invalid workflow mode. Must be NONE, SEPARATE, or COMBINED")
    private String workflowMode;

    @NotNull(message = "Assignment mode is required")
    @Pattern(regexp = "MANUAL|AUTO|BOTH", message = "Invalid assignment mode. Must be MANUAL, AUTO, or BOTH")
    private String assignmentMode;

    @NotNull(message = "Acknowledgment requirement must be specified")
    private Boolean requireAcknowledgment;

    @NotBlank(message = "Linked threshold ID is required")
    private String linkedThresholdId;

    private String description;

    private Boolean isActive;

    private String createdBy;

    private String updatedBy;
}

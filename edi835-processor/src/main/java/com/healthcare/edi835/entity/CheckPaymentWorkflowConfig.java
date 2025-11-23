package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Check Payment Workflow Configuration entity.
 * Defines how check payment workflows are handled for specific thresholds/bucketing rules.
 *
 * <p>Workflow Modes:</p>
 * <ul>
 *   <li>NONE: No check payment required (EFT or other payment method)</li>
 *   <li>SEPARATE: Approve bucket first, then assign check in separate step</li>
 *   <li>COMBINED: Approve and assign check in single dialog</li>
 * </ul>
 *
 * <p>Assignment Modes:</p>
 * <ul>
 *   <li>MANUAL: User manually enters check details (check number, date, bank info)</li>
 *   <li>AUTO: System auto-assigns from pre-reserved check number ranges</li>
 *   <li>BOTH: User can choose between manual entry or auto-assignment</li>
 * </ul>
 */
@Entity
@Table(name = "check_payment_workflow_config", indexes = {
        @Index(name = "idx_workflow_config_threshold", columnList = "linked_threshold_id"),
        @Index(name = "idx_workflow_config_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPaymentWorkflowConfig {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "config_name", nullable = false)
    private String configName;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_mode", nullable = false, length = 20)
    @Builder.Default
    private WorkflowMode workflowMode = WorkflowMode.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", nullable = false, length = 20)
    @Builder.Default
    private AssignmentMode assignmentMode = AssignmentMode.MANUAL;

    @Column(name = "require_acknowledgment")
    @Builder.Default
    private Boolean requireAcknowledgment = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_threshold_id", unique = true)
    private EdiGenerationThreshold linkedThreshold;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Workflow mode enum - defines the approval + check assignment flow.
     */
    public enum WorkflowMode {
        /**
         * No check payment required.
         * Bucket uses EFT or other payment method.
         * Approval triggers EDI generation immediately.
         */
        NONE,

        /**
         * Separate workflow: Approve first, then assign check separately.
         * After approval, bucket awaits check assignment.
         * Check assignment triggers EDI generation.
         */
        SEPARATE,

        /**
         * Combined workflow: Approve and assign check in single dialog.
         * User approves bucket and enters check details in one step.
         * EDI generation triggered after both approval and check assignment.
         */
        COMBINED
    }

    /**
     * Assignment mode enum - defines how checks are assigned.
     */
    public enum AssignmentMode {
        /**
         * Manual assignment only.
         * User must enter check number, date, and bank details manually.
         */
        MANUAL,

        /**
         * Automatic assignment only.
         * System auto-assigns next available check from pre-reserved ranges.
         */
        AUTO,

        /**
         * Both manual and automatic.
         * User can choose to enter check details manually or use auto-assignment.
         */
        BOTH
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if this configuration requires check payment.
     *
     * @return true if workflow mode is not NONE
     */
    public boolean requiresCheckPayment() {
        return workflowMode != WorkflowMode.NONE;
    }

    /**
     * Checks if workflow uses combined approval + assignment dialog.
     *
     * @return true if workflow mode is COMBINED
     */
    public boolean isCombinedWorkflow() {
        return workflowMode == WorkflowMode.COMBINED;
    }

    /**
     * Checks if workflow uses separate approval and assignment steps.
     *
     * @return true if workflow mode is SEPARATE
     */
    public boolean isSeparateWorkflow() {
        return workflowMode == WorkflowMode.SEPARATE;
    }

    /**
     * Checks if manual assignment is allowed.
     *
     * @return true if assignment mode is MANUAL or BOTH
     */
    public boolean allowsManualAssignment() {
        return assignmentMode == AssignmentMode.MANUAL || assignmentMode == AssignmentMode.BOTH;
    }

    /**
     * Checks if automatic assignment is allowed.
     *
     * @return true if assignment mode is AUTO or BOTH
     */
    public boolean allowsAutoAssignment() {
        return assignmentMode == AssignmentMode.AUTO || assignmentMode == AssignmentMode.BOTH;
    }

    /**
     * Checks if acknowledgment is required before EDI generation.
     *
     * @return true if require_acknowledgment is enabled
     */
    public boolean requiresAcknowledgment() {
        return requireAcknowledgment != null && requireAcknowledgment;
    }
}

package com.healthcare.edi835.entity;

import com.healthcare.edi835.config.StringArrayConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EDI Commit Criteria entity - defines AUTO/MANUAL/HYBRID commit modes.
 * Maps to 'edi_commit_criteria' table in PostgreSQL.
 */
@Entity
@Table(name = "edi_commit_criteria", indexes = {
        @Index(name = "idx_commit_criteria_rule", columnList = "linkedBucketingRuleId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdiCommitCriteria {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "criteria_name", nullable = false)
    private String criteriaName;

    @Enumerated(EnumType.STRING)
    @Column(name = "commit_mode", nullable = false, length = 20)
    private CommitMode commitMode;

    @Column(name = "auto_commit_threshold")
    private Integer autoCommitThreshold;

    @Column(name = "manual_approval_threshold")
    private Integer manualApprovalThreshold;

    @Column(name = "approval_required_roles", columnDefinition = "TEXT")
    @Convert(converter = StringArrayConverter.class)
    private String[] approvalRequiredRoles;

    @Column(name = "override_permissions", columnDefinition = "TEXT")
    @Convert(converter = StringArrayConverter.class)
    private String[] overridePermissions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_bucketing_rule_id")
    private EdiBucketingRule linkedBucketingRule;

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

    public enum CommitMode {
        AUTO,
        MANUAL,
        HYBRID
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Wrapper methods for backward compatibility
    public Integer getApprovalClaimCountThreshold() {
        return manualApprovalThreshold;
    }

    public java.math.BigDecimal getApprovalAmountThreshold() {
        // Assuming a monetary threshold - convert from integer to BigDecimal if needed
        return manualApprovalThreshold != null ?
            java.math.BigDecimal.valueOf(manualApprovalThreshold) : null;
    }

    public String getApprovalRoles() {
        if (approvalRequiredRoles == null || approvalRequiredRoles.length == 0) {
            return null;
        }
        return String.join(",", approvalRequiredRoles);
    }

    // Setter for criteriaId (used in controllers)
    public void setCriteriaId(UUID criteriaId) {
        this.id = criteriaId;
    }

    public UUID getCriteriaId() {
        return this.id;
    }
}

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
 * EDI Bucketing Rule entity - defines how claims are grouped into buckets.
 * Maps to 'edi_bucketing_rules' table in PostgreSQL.
 */
@Entity
@Table(name = "edi_bucketing_rules", indexes = {
        @Index(name = "idx_bucketing_rules_active", columnList = "isActive"),
        @Index(name = "idx_bucketing_rules_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdiBucketingRule {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "rule_name", unique = true, nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;

    @Column(name = "grouping_expression", columnDefinition = "TEXT")
    private String groupingExpression;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_payer_id")
    private Payer linkedPayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_payee_id")
    private Payee linkedPayee;

    @Column(name = "description", columnDefinition = "TEXT")
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

    public enum RuleType {
        PAYER_PAYEE,
        BIN_PCN,
        CUSTOM
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Wrapper methods for backward compatibility
    public void setRuleId(UUID ruleId) {
        this.id = ruleId;
    }

    public UUID getRuleId() {
        return this.id;
    }
}

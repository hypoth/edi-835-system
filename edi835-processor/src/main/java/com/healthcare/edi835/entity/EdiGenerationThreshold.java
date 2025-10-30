package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EDI Generation Threshold entity - defines when EDI files should be generated.
 * Maps to 'edi_generation_thresholds' table in PostgreSQL.
 */
@Entity
@Table(name = "edi_generation_thresholds", indexes = {
        @Index(name = "idx_gen_thresholds_rule", columnList = "linkedBucketingRuleId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdiGenerationThreshold {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "threshold_name", nullable = false)
    private String thresholdName;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_type", nullable = false, length = 50)
    private ThresholdType thresholdType;

    @Column(name = "max_claims")
    private Integer maxClaims;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_duration", length = 20)
    private TimeDuration timeDuration;

    @Column(name = "generation_schedule", length = 100)
    private String generationSchedule;  // Cron expression or time specification

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

    public enum ThresholdType {
        CLAIM_COUNT,
        AMOUNT,
        TIME,
        HYBRID
    }

    public enum TimeDuration {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Wrapper methods for backward compatibility
    public void setThresholdId(UUID thresholdId) {
        this.id = thresholdId;
    }

    public UUID getThresholdId() {
        return this.id;
    }
}

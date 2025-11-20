package com.healthcare.edi835.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * EDI File Bucket entity - represents an active accumulation of claims.
 * Maps to 'edi_file_buckets' table in PostgreSQL.
 */
@Entity
@Table(name = "edi_file_buckets", indexes = {
        @Index(name = "idx_buckets_status", columnList = "status"),
        @Index(name = "idx_buckets_payer_payee", columnList = "payerId, payeeId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EdiFileBucket {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "bucket_id", updatable = false, nullable = false)
    private UUID bucketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private BucketStatus status = BucketStatus.ACCUMULATING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucketing_rule_id")
    @JsonIgnore
    private EdiBucketingRule bucketingRule;

    @Column(name = "bucketing_rule_name")
    private String bucketingRuleName;

    @Column(name = "payer_id", nullable = false, length = 50)
    private String payerId;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payee_id", nullable = false, length = 50)
    private String payeeId;

    @Column(name = "payee_name")
    private String payeeName;

    @Column(name = "bin_number", length = 20)
    private String binNumber;

    @Column(name = "pcn_number", length = 20)
    private String pcnNumber;

    @Column(name = "claim_count")
    @Builder.Default
    private Integer claimCount = 0;

    @Column(name = "total_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "rejection_count")
    @Builder.Default
    private Integer rejectionCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_naming_template_id")
    @JsonIgnore
    private EdiFileNamingTemplate fileNamingTemplate;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_updated")
    @JsonProperty("updatedAt")
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Column(name = "awaiting_approval_since")
    private LocalDateTime awaitingApprovalSince;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "generation_started_at")
    private LocalDateTime generationStartedAt;

    @Column(name = "generation_completed_at")
    private LocalDateTime generationCompletedAt;

    public enum BucketStatus {
        ACCUMULATING,
        PENDING_APPROVAL,
        GENERATING,
        COMPLETED,
        FAILED,
        MISSING_CONFIGURATION  // Missing payer or payee configuration
    }

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Transitions bucket to PENDING_APPROVAL status.
     */
    public void markPendingApproval() {
        this.status = BucketStatus.PENDING_APPROVAL;
        this.awaitingApprovalSince = LocalDateTime.now();
    }

    /**
     * Transitions bucket to GENERATING status.
     */
    public void markGenerating() {
        this.status = BucketStatus.GENERATING;
        this.generationStartedAt = LocalDateTime.now();
    }

    /**
     * Transitions bucket to COMPLETED status.
     */
    public void markCompleted() {
        this.status = BucketStatus.COMPLETED;
        this.generationCompletedAt = LocalDateTime.now();
    }

    /**
     * Transitions bucket to FAILED status.
     */
    public void markFailed() {
        this.status = BucketStatus.FAILED;
    }

    /**
     * Transitions bucket to MISSING_CONFIGURATION status.
     */
    public void markMissingConfiguration() {
        this.status = BucketStatus.MISSING_CONFIGURATION;
    }

    /**
     * Increments claim count and updates total amount.
     */
    public void addClaim(BigDecimal claimAmount) {
        this.claimCount++;
        this.totalAmount = this.totalAmount.add(claimAmount);
    }

    /**
     * Gets the age of the bucket in days since creation.
     */
    public long getAgeDays() {
        if (createdAt == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
    }

    /**
     * Gets bucket ID (alias for id field).
     */
    public UUID getBucketId() {
        return this.bucketId;
    }
}

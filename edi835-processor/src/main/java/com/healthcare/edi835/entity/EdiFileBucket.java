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
import java.time.LocalDate;
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

    // Error tracking for failed buckets
    @Column(name = "last_error_message", length = 2000)
    private String lastErrorMessage;

    @Column(name = "last_error_at")
    private LocalDateTime lastErrorAt;

    // Payment tracking fields (Phase 1: Check Payment Implementation)
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_required")
    @Builder.Default
    private Boolean paymentRequired = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_payment_id")
    @JsonIgnore
    private CheckPayment checkPayment;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /**
     * Payment status lifecycle for check payments:
     * PENDING → ASSIGNED → ACKNOWLEDGED → ISSUED
     */
    public enum PaymentStatus {
        PENDING,      // No check assigned yet
        ASSIGNED,     // Check number assigned to bucket
        ACKNOWLEDGED, // Check amount acknowledged by user
        ISSUED        // Check physically issued
    }

    public enum BucketStatus {
        ACCUMULATING,
        PENDING_APPROVAL,
        GENERATING,
        COMPLETED,
        FAILED,
        MISSING_CONFIGURATION  // Missing payer or payee configuration
    }

    /**
     * Get the bucketing rule ID without loading the full entity.
     * This is exposed in JSON responses for frontend to fetch workflow configs.
     */
    @JsonProperty("bucketingRuleId")
    public UUID getBucketingRuleId() {
        return bucketingRule != null ? bucketingRule.getId() : null;
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
     * Transitions bucket to FAILED status with an error message.
     *
     * @param errorMessage The error message describing why the bucket failed
     */
    public void markFailed(String errorMessage) {
        this.status = BucketStatus.FAILED;
        this.lastErrorMessage = truncateErrorMessage(errorMessage);
        this.lastErrorAt = LocalDateTime.now();
    }

    /**
     * Transitions bucket to FAILED status with exception details.
     *
     * @param exception The exception that caused the failure
     */
    public void markFailed(Throwable exception) {
        this.status = BucketStatus.FAILED;
        this.lastErrorMessage = truncateErrorMessage(formatExceptionMessage(exception));
        this.lastErrorAt = LocalDateTime.now();
    }

    /**
     * Sets the last error message without changing status.
     * Useful for recording errors during processing that don't immediately fail the bucket.
     *
     * @param errorMessage The error message
     */
    public void setLastError(String errorMessage) {
        this.lastErrorMessage = truncateErrorMessage(errorMessage);
        this.lastErrorAt = LocalDateTime.now();
    }

    /**
     * Formats exception into a readable error message including cause chain.
     */
    private String formatExceptionMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getSimpleName()).append(": ").append(exception.getMessage());

        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            sb.append(" | Caused by: ").append(cause.getClass().getSimpleName())
              .append(": ").append(cause.getMessage());
        }
        return sb.toString();
    }

    /**
     * Truncates error message to fit within database column limit.
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 1997) + "..." : message;
    }

    /**
     * Transitions bucket to MISSING_CONFIGURATION status.
     */
    public void markMissingConfiguration() {
        this.status = BucketStatus.MISSING_CONFIGURATION;
    }

    /**
     * Assigns a check payment to this bucket.
     *
     * @param checkPayment The check payment to assign
     */
    public void assignCheckPayment(CheckPayment checkPayment) {
        if (this.paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("Cannot assign check - bucket payment status is %s", this.paymentStatus));
        }
        this.checkPayment = checkPayment;
        this.paymentStatus = PaymentStatus.ASSIGNED;
        this.paymentDate = checkPayment.getCheckDate();
    }

    /**
     * Resets payment status to PENDING to allow check replacement.
     * Only allowed for buckets in PENDING_APPROVAL status with ASSIGNED payment.
     *
     * @return the previous CheckPayment that was removed
     * @throws IllegalStateException if bucket is not in correct state for replacement
     */
    public CheckPayment resetPaymentForReplacement() {
        if (this.status != BucketStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    String.format("Cannot replace check - bucket status must be PENDING_APPROVAL, but is %s", this.status));
        }
        if (this.paymentStatus != PaymentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    String.format("Cannot replace check - payment status must be ASSIGNED, but is %s", this.paymentStatus));
        }

        CheckPayment previousCheck = this.checkPayment;
        this.checkPayment = null;
        this.paymentStatus = PaymentStatus.PENDING;
        this.paymentDate = null;

        return previousCheck;
    }

    /**
     * Marks check payment as acknowledged.
     */
    public void markPaymentAcknowledged() {
        if (this.paymentStatus != PaymentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    String.format("Cannot acknowledge - bucket payment status is %s", this.paymentStatus));
        }
        this.paymentStatus = PaymentStatus.ACKNOWLEDGED;
    }

    /**
     * Marks check payment as issued.
     */
    public void markPaymentIssued() {
        if (this.paymentStatus != PaymentStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    String.format("Cannot mark as issued - bucket payment status is %s", this.paymentStatus));
        }
        this.paymentStatus = PaymentStatus.ISSUED;
    }

    /**
     * Checks if payment is required for this bucket.
     *
     * @return true if payment is required
     */
    public boolean isPaymentRequired() {
        return this.paymentRequired != null && this.paymentRequired;
    }

    /**
     * Checks if payment has been assigned to this bucket.
     *
     * @return true if check payment is assigned
     */
    public boolean hasPaymentAssigned() {
        return this.checkPayment != null;
    }

    /**
     * Checks if payment is ready for EDI generation.
     * Considers configuration setting for acknowledgment requirement.
     *
     * @param requireAcknowledgmentBeforeEdi Config setting
     * @return true if payment is ready
     */
    public boolean isPaymentReadyForEdi(boolean requireAcknowledgmentBeforeEdi) {
        if (!isPaymentRequired()) {
            return true;  // No payment required
        }
        if (!hasPaymentAssigned()) {
            return false;  // Payment required but not assigned
        }
        if (requireAcknowledgmentBeforeEdi) {
            // Must be acknowledged or issued
            return this.paymentStatus == PaymentStatus.ACKNOWLEDGED ||
                   this.paymentStatus == PaymentStatus.ISSUED;
        } else {
            // Just needs to be assigned
            return this.paymentStatus == PaymentStatus.ASSIGNED ||
                   this.paymentStatus == PaymentStatus.ACKNOWLEDGED ||
                   this.paymentStatus == PaymentStatus.ISSUED;
        }
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

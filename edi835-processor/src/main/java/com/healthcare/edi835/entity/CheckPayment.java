package com.healthcare.edi835.entity;

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
 * CheckPayment entity - represents a check payment assignment to a bucket.
 * Maps to 'check_payments' table.
 *
 * Supports two workflows:
 * 1. Manual: User enters check details during approval
 * 2. Auto: System uses pre-reserved check numbers
 */
@Entity
@Table(name = "check_payments", indexes = {
        @Index(name = "idx_check_payments_bucket", columnList = "bucket_id"),
        @Index(name = "idx_check_payments_status", columnList = "status"),
        @Index(name = "idx_check_payments_check_number", columnList = "checkNumber"),
        @Index(name = "idx_check_payments_payment_method", columnList = "payment_method_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPayment {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private EdiFileBucket bucket;

    @Column(name = "check_number", unique = true, nullable = false)
    private String checkNumber;  // Alphanumeric, variable length (e.g., CHK000123)

    @Column(name = "check_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal checkAmount;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "routing_number")
    private String routingNumber;

    @Column(name = "account_number_last4")
    private String accountNumberLast4;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CheckStatus status = CheckStatus.RESERVED;

    // Assignment tracking
    @Column(name = "assigned_by")
    private String assignedBy;  // User who assigned (manual) or 'SYSTEM' (auto)

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    // Acknowledgment tracking
    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    // Issuance tracking
    @Column(name = "issued_by")
    private String issuedBy;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    // Void tracking
    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "voided_by")
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    // Audit fields
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Check status lifecycle:
     * RESERVED → ASSIGNED → ACKNOWLEDGED → ISSUED → (VOID/CANCELLED)
     */
    public enum CheckStatus {
        RESERVED,      // Check number reserved but not assigned to bucket
        ASSIGNED,      // Assigned to a bucket, awaiting acknowledgment
        ACKNOWLEDGED,  // Amount acknowledged by user, ready for issuance
        ISSUED,        // Check physically issued/mailed
        VOID,          // Check voided (requires FINANCIAL_ADMIN role)
        CANCELLED      // Check cancelled before issuance
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Transitions check to ASSIGNED status.
     */
    public void markAssigned(String assignedBy) {
        this.status = CheckStatus.ASSIGNED;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
    }

    /**
     * Transitions check to ACKNOWLEDGED status.
     */
    public void markAcknowledged(String acknowledgedBy) {
        if (this.status != CheckStatus.ASSIGNED) {
            throw new IllegalStateException("Check must be ASSIGNED before acknowledgment");
        }
        this.status = CheckStatus.ACKNOWLEDGED;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = LocalDateTime.now();
    }

    /**
     * Transitions check to ISSUED status.
     */
    public void markIssued(String issuedBy) {
        if (this.status != CheckStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Check must be ACKNOWLEDGED before issuance");
        }
        this.status = CheckStatus.ISSUED;
        this.issuedBy = issuedBy;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * Voids the check.
     *
     * @param reason Reason for voiding
     * @param voidedBy User voiding the check (must have FINANCIAL_ADMIN role)
     */
    public void markVoid(String reason, String voidedBy) {
        this.status = CheckStatus.VOID;
        this.voidReason = reason;
        this.voidedBy = voidedBy;
        this.voidedAt = LocalDateTime.now();
    }

    /**
     * Cancels the check (before issuance).
     */
    public void markCancelled() {
        if (this.status == CheckStatus.ISSUED) {
            throw new IllegalStateException("Cannot cancel an issued check - use void instead");
        }
        this.status = CheckStatus.CANCELLED;
    }

    /**
     * Checks if check can be voided based on time limit.
     *
     * @param voidTimeLimitHours Time limit in hours
     * @return true if within void window
     */
    public boolean canBeVoided(int voidTimeLimitHours) {
        if (this.status != CheckStatus.ISSUED || this.issuedAt == null) {
            return false;
        }
        LocalDateTime voidDeadline = this.issuedAt.plusHours(voidTimeLimitHours);
        return LocalDateTime.now().isBefore(voidDeadline);
    }

    /**
     * Checks if check is in a final state (issued, void, cancelled).
     */
    public boolean isFinalState() {
        return this.status == CheckStatus.ISSUED
            || this.status == CheckStatus.VOID
            || this.status == CheckStatus.CANCELLED;
    }
}

package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CheckAuditLog entity - comprehensive audit trail for all check payment operations.
 * Maps to 'check_audit_log' table.
 *
 * Records every action performed on check payments including:
 * - Check assignment to buckets
 * - Status transitions (ASSIGNED → ACKNOWLEDGED → ISSUED)
 * - Void operations with reasons
 * - Amount acknowledgments
 * - Any manual interventions
 */
@Entity
@Table(name = "check_audit_log", indexes = {
        @Index(name = "idx_check_audit_check_payment", columnList = "check_payment_id"),
        @Index(name = "idx_check_audit_check_number", columnList = "check_number"),
        @Index(name = "idx_check_audit_bucket", columnList = "bucket_id"),
        @Index(name = "idx_check_audit_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_payment_id")
    private CheckPayment checkPayment;

    @Column(name = "check_number", nullable = false)
    private String checkNumber;

    @Column(name = "action", nullable = false)
    private String action;  // e.g., "ASSIGNED", "ACKNOWLEDGED", "ISSUED", "VOIDED", "CANCELLED"

    @Column(name = "bucket_id")
    private String bucketId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "performed_by")
    private String performedBy;  // User who performed the action or "SYSTEM" for automated actions

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;  // Additional context or reason for the action

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Common action types for standardized logging.
     */
    public static class Actions {
        public static final String RESERVED = "RESERVED";
        public static final String ASSIGNED = "ASSIGNED";
        public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
        public static final String ISSUED = "ISSUED";
        public static final String VOIDED = "VOIDED";
        public static final String CANCELLED = "CANCELLED";
        public static final String AMOUNT_UPDATED = "AMOUNT_UPDATED";
        public static final String MANUAL_ENTRY = "MANUAL_ENTRY";
        public static final String AUTO_ASSIGNED = "AUTO_ASSIGNED";
    }

    /**
     * Creates an audit log entry for check assignment.
     *
     * @param checkPayment The check payment
     * @param bucketId The bucket ID
     * @param performedBy User performing the action
     * @param isAutomatic Whether assignment was automatic
     * @return CheckAuditLog instance
     */
    public static CheckAuditLog logAssignment(CheckPayment checkPayment, String bucketId,
                                              String performedBy, boolean isAutomatic) {
        return CheckAuditLog.builder()
                .checkPayment(checkPayment)
                .checkNumber(checkPayment.getCheckNumber())
                .action(isAutomatic ? Actions.AUTO_ASSIGNED : Actions.ASSIGNED)
                .bucketId(bucketId)
                .amount(checkPayment.getCheckAmount())
                .performedBy(performedBy)
                .notes(isAutomatic ? "Automatically assigned from reservation" : "Manually assigned during approval")
                .build();
    }

    /**
     * Creates an audit log entry for check acknowledgment.
     *
     * @param checkPayment The check payment
     * @param performedBy User acknowledging the check
     * @return CheckAuditLog instance
     */
    public static CheckAuditLog logAcknowledgment(CheckPayment checkPayment, String performedBy) {
        return CheckAuditLog.builder()
                .checkPayment(checkPayment)
                .checkNumber(checkPayment.getCheckNumber())
                .action(Actions.ACKNOWLEDGED)
                .bucketId(checkPayment.getBucket() != null ? checkPayment.getBucket().getBucketId().toString() : null)
                .amount(checkPayment.getCheckAmount())
                .performedBy(performedBy)
                .notes("Check amount acknowledged")
                .build();
    }

    /**
     * Creates an audit log entry for check issuance.
     *
     * @param checkPayment The check payment
     * @param performedBy User issuing the check
     * @return CheckAuditLog instance
     */
    public static CheckAuditLog logIssuance(CheckPayment checkPayment, String performedBy) {
        return CheckAuditLog.builder()
                .checkPayment(checkPayment)
                .checkNumber(checkPayment.getCheckNumber())
                .action(Actions.ISSUED)
                .bucketId(checkPayment.getBucket() != null ? checkPayment.getBucket().getBucketId().toString() : null)
                .amount(checkPayment.getCheckAmount())
                .performedBy(performedBy)
                .notes("Check physically issued")
                .build();
    }

    /**
     * Creates an audit log entry for check void.
     *
     * @param checkPayment The check payment
     * @param reason Reason for voiding
     * @param performedBy User voiding the check
     * @return CheckAuditLog instance
     */
    public static CheckAuditLog logVoid(CheckPayment checkPayment, String reason, String performedBy) {
        return CheckAuditLog.builder()
                .checkPayment(checkPayment)
                .checkNumber(checkPayment.getCheckNumber())
                .action(Actions.VOIDED)
                .bucketId(checkPayment.getBucket() != null ? checkPayment.getBucket().getBucketId().toString() : null)
                .amount(checkPayment.getCheckAmount())
                .performedBy(performedBy)
                .notes("Voided: " + reason)
                .build();
    }

    /**
     * Creates an audit log entry for check cancellation.
     *
     * @param checkPayment The check payment
     * @param performedBy User cancelling the check
     * @return CheckAuditLog instance
     */
    public static CheckAuditLog logCancellation(CheckPayment checkPayment, String performedBy) {
        return CheckAuditLog.builder()
                .checkPayment(checkPayment)
                .checkNumber(checkPayment.getCheckNumber())
                .action(Actions.CANCELLED)
                .bucketId(checkPayment.getBucket() != null ? checkPayment.getBucket().getBucketId().toString() : null)
                .amount(checkPayment.getCheckAmount())
                .performedBy(performedBy)
                .notes("Check cancelled before issuance")
                .build();
    }

    /**
     * Creates an audit log entry for amount updates.
     *
     * @param checkPayment The check payment
     * @param oldAmount Previous amount
     * @param newAmount New amount
     * @param performedBy User updating the amount
     * @return CheckAuditLog instance
     */
    public static CheckAuditLog logAmountUpdate(CheckPayment checkPayment, BigDecimal oldAmount,
                                                BigDecimal newAmount, String performedBy) {
        return CheckAuditLog.builder()
                .checkPayment(checkPayment)
                .checkNumber(checkPayment.getCheckNumber())
                .action(Actions.AMOUNT_UPDATED)
                .bucketId(checkPayment.getBucket() != null ? checkPayment.getBucket().getBucketId().toString() : null)
                .amount(newAmount)
                .performedBy(performedBy)
                .notes(String.format("Amount changed from %s to %s", oldAmount, newAmount))
                .build();
    }
}

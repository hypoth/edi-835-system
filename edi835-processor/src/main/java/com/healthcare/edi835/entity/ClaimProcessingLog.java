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
 * Claim Processing Log entity - audit trail for claim processing.
 * Maps to 'claim_processing_log' table in PostgreSQL.
 */
@Entity
@Table(name = "claim_processing_log", indexes = {
        @Index(name = "idx_claim_log_bucket", columnList = "bucketId"),
        @Index(name = "idx_claim_log_claim", columnList = "claimId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimProcessingLog {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "claim_id", nullable = false, length = 50)
    private String claimId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id")
    private EdiFileBucket bucket;

    @Column(name = "payer_id", length = 50)
    private String payerId;

    @Column(name = "payee_id", length = 50)
    private String payeeId;

    @Column(name = "claim_amount", precision = 15, scale = 2)
    private BigDecimal claimAmount;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "adjustment_amount", precision = 15, scale = 2)
    private BigDecimal adjustmentAmount;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "processed_at")
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    /**
     * Creates a log entry for a successfully processed claim.
     */
    public static ClaimProcessingLog forProcessedClaim(String claimId, EdiFileBucket bucket,
                                                       String payerId, String payeeId,
                                                       BigDecimal claimAmount, BigDecimal paidAmount) {
        return ClaimProcessingLog.builder()
                .claimId(claimId)
                .bucket(bucket)
                .payerId(payerId)
                .payeeId(payeeId)
                .claimAmount(claimAmount)
                .paidAmount(paidAmount)
                .status("PROCESSED")
                .build();
    }

    /**
     * Creates a log entry for a rejected claim.
     */
    public static ClaimProcessingLog forRejectedClaim(String claimId, String payerId,
                                                      String payeeId, String rejectionReason) {
        return ClaimProcessingLog.builder()
                .claimId(claimId)
                .payerId(payerId)
                .payeeId(payeeId)
                .status("REJECTED")
                .rejectionReason(rejectionReason)
                .build();
    }
}

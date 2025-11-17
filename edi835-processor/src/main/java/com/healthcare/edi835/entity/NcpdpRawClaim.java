package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a raw NCPDP D.0 pharmacy prescription claim.
 *
 * <p>This table stores unprocessed NCPDP transactions and is monitored by
 * the NcpdpChangeFeedProcessor. When a new claim is inserted with status='PENDING',
 * the change feed processor automatically parses, maps, and processes it.</p>
 *
 * <p><strong>Status Flow:</strong></p>
 * <ul>
 *   <li>PENDING → PROCESSING → PROCESSED (success)</li>
 *   <li>PENDING → PROCESSING → FAILED (error, can be retried)</li>
 * </ul>
 *
 * @see com.healthcare.edi835.changefeed.NcpdpChangeFeedProcessor
 */
@Entity
@Table(name = "ncpdp_raw_claims")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NcpdpRawClaim {

    /**
     * Unique identifier (UUID)
     */
    @Id
    @Column(name = "id", length = 50)
    private String id;

    /**
     * Payer ID extracted from NCPDP AM07 segment (carrier ID).
     * Used for indexing and quick lookups.
     * Examples: BCBSIL, CIGNA, AETNA, UNITEDHC
     */
    @Column(name = "payer_id", length = 50, nullable = false)
    private String payerId;

    /**
     * Pharmacy ID from NCPDP AM01 segment (service provider ID).
     * Examples: PHARMACY001, PHARMACY002
     */
    @Column(name = "pharmacy_id", length = 50)
    private String pharmacyId;

    /**
     * Transaction ID for tracking and correlation
     */
    @Column(name = "transaction_id", length = 50)
    private String transactionId;

    /**
     * Complete raw NCPDP D.0 transaction content (from STX to SE segment).
     * This includes all segments: STX, AM01, AM04, AM07, AM11, AM13, AM15, AM17, AN*, SE.
     *
     * <p>Storing the raw content enables:</p>
     * <ul>
     *   <li>Re-processing if parsing logic changes</li>
     *   <li>Audit trail of original data</li>
     *   <li>Debugging parsing issues</li>
     * </ul>
     */
    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    private String rawContent;

    /**
     * Transaction type from NCPDP standard.
     * Examples: B1 (Billing), B2 (Reversal), B3 (Rebill)
     */
    @Column(name = "transaction_type", length = 10)
    private String transactionType;

    /**
     * Date of service from NCPDP AM13 segment
     */
    @Column(name = "service_date")
    private LocalDate serviceDate;

    /**
     * Patient cardholder ID from NCPDP AM07 segment
     */
    @Column(name = "patient_id", length = 50)
    private String patientId;

    /**
     * Prescription/service reference number from NCPDP AM13 segment
     */
    @Column(name = "prescription_number", length = 50)
    private String prescriptionNumber;

    /**
     * Processing status of the NCPDP claim.
     *
     * <p>Values:</p>
     * <ul>
     *   <li>PENDING: Inserted, awaiting processing by change feed</li>
     *   <li>PROCESSING: Currently being parsed and converted</li>
     *   <li>PROCESSED: Successfully converted to Claim and forwarded</li>
     *   <li>FAILED: Processing error occurred, can be retried</li>
     * </ul>
     */
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NcpdpStatus status = NcpdpStatus.PENDING;

    /**
     * Timestamp when the raw claim was inserted
     */
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    /**
     * Timestamp when processing completed (either PROCESSED or FAILED)
     */
    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    /**
     * Timestamp when processing started (status changed to PROCESSING).
     * Used to detect stuck claims that remain in PROCESSING status too long.
     */
    @Column(name = "processing_started_date")
    private LocalDateTime processingStartedDate;

    /**
     * ID of the processed Claim entity after successful conversion.
     * Links to the Claim.id in the main claims table/container.
     */
    @Column(name = "claim_id", length = 50)
    private String claimId;

    /**
     * Error message if processing failed.
     * Used for debugging and retry decisions.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Number of times processing has been attempted.
     * Used to prevent infinite retry loops.
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * User or system that created this record
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Lifecycle callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
        if (status == null) {
            status = NcpdpStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * Enum representing NCPDP claim processing status
     */
    public enum NcpdpStatus {
        /**
         * Claim inserted, waiting for change feed processor
         */
        PENDING,

        /**
         * Currently being parsed and processed
         */
        PROCESSING,

        /**
         * Successfully converted to Claim and processed
         */
        PROCESSED,

        /**
         * Processing failed, error logged, can be retried
         */
        FAILED
    }

    /**
     * Marks claim as processing and records the start time
     */
    public void markAsProcessing() {
        this.status = NcpdpStatus.PROCESSING;
        this.processingStartedDate = LocalDateTime.now();
    }

    /**
     * Marks claim as successfully processed
     * @param processedClaimId ID of the generated Claim
     */
    public void markAsProcessed(String processedClaimId) {
        this.status = NcpdpStatus.PROCESSED;
        this.processedDate = LocalDateTime.now();
        this.claimId = processedClaimId;
        this.errorMessage = null;
    }

    /**
     * Marks claim as failed with error message
     * @param errorMsg Description of the error
     */
    public void markAsFailed(String errorMsg) {
        this.status = NcpdpStatus.FAILED;
        this.processedDate = LocalDateTime.now();
        this.errorMessage = errorMsg;
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
    }

    /**
     * Checks if claim can be retried (not exceeded max retries)
     * @param maxRetries Maximum number of retry attempts
     * @return true if can be retried
     */
    public boolean canRetry(int maxRetries) {
        return this.status == NcpdpStatus.FAILED &&
               this.retryCount < maxRetries;
    }
}

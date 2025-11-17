package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.NcpdpRawClaim;
import com.healthcare.edi835.entity.NcpdpRawClaim.NcpdpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for NCPDP raw claims data access.
 *
 * <p>This repository provides methods to query and manage raw NCPDP D.0
 * pharmacy prescription claims. The change feed processor uses this to
 * retrieve pending claims for processing.</p>
 *
 * @see NcpdpRawClaim
 * @see com.healthcare.edi835.changefeed.NcpdpChangeFeedProcessor
 */
@Repository
public interface NcpdpRawClaimRepository extends JpaRepository<NcpdpRawClaim, String> {

    /**
     * Find all claims by status
     *
     * @param status the processing status
     * @return list of claims with the given status
     */
    List<NcpdpRawClaim> findByStatus(NcpdpStatus status);

    /**
     * Find claims by status ordered by creation date (oldest first).
     * Used by change feed processor to process claims in FIFO order.
     *
     * @param status the processing status
     * @return list of claims ordered by created date ascending
     */
    List<NcpdpRawClaim> findByStatusOrderByCreatedDateAsc(NcpdpStatus status);

    /**
     * Find claims by payer ID
     *
     * @param payerId the payer identifier
     * @return list of claims for the payer
     */
    List<NcpdpRawClaim> findByPayerId(String payerId);

    /**
     * Find claims by pharmacy ID
     *
     * @param pharmacyId the pharmacy identifier
     * @return list of claims from the pharmacy
     */
    List<NcpdpRawClaim> findByPharmacyId(String pharmacyId);

    /**
     * Find claim by prescription number
     *
     * @param prescriptionNumber the prescription/service reference number
     * @return optional NCPDP claim
     */
    Optional<NcpdpRawClaim> findByPrescriptionNumber(String prescriptionNumber);

    /**
     * Find claim by the processed claim ID
     *
     * @param claimId the processed Claim.id
     * @return optional NCPDP claim
     */
    Optional<NcpdpRawClaim> findByClaimId(String claimId);

    /**
     * Find claims by service date
     *
     * @param serviceDate the date of service
     * @return list of claims for the service date
     */
    List<NcpdpRawClaim> findByServiceDate(LocalDate serviceDate);

    /**
     * Find claims by service date range
     *
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of claims in date range
     */
    List<NcpdpRawClaim> findByServiceDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find claims created after a specific date/time
     *
     * @param createdDate the creation timestamp
     * @return list of claims created after the date
     */
    List<NcpdpRawClaim> findByCreatedDateAfter(LocalDateTime createdDate);

    /**
     * Find failed claims that can be retried (retry count < maxRetries)
     *
     * @param maxRetries maximum number of retries allowed
     * @return list of failed claims eligible for retry
     */
    @Query("SELECT n FROM NcpdpRawClaim n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries")
    List<NcpdpRawClaim> findFailedClaimsForRetry(@Param("maxRetries") int maxRetries);

    /**
     * Count claims by status
     *
     * @param status the processing status
     * @return count of claims with the status
     */
    @Query("SELECT COUNT(n) FROM NcpdpRawClaim n WHERE n.status = :status")
    long countByStatus(@Param("status") NcpdpStatus status);

    /**
     * Count pending claims (convenience method)
     *
     * @return count of pending claims
     */
    default long countPending() {
        return countByStatus(NcpdpStatus.PENDING);
    }

    /**
     * Count processing claims (convenience method)
     *
     * @return count of processing claims
     */
    default long countProcessing() {
        return countByStatus(NcpdpStatus.PROCESSING);
    }

    /**
     * Count processed claims (convenience method)
     *
     * @return count of processed claims
     */
    default long countProcessed() {
        return countByStatus(NcpdpStatus.PROCESSED);
    }

    /**
     * Count failed claims (convenience method)
     *
     * @return count of failed claims
     */
    default long countFailed() {
        return countByStatus(NcpdpStatus.FAILED);
    }

    /**
     * Find claims by payer ID and status
     *
     * @param payerId the payer identifier
     * @param status the processing status
     * @return list of claims matching both criteria
     */
    List<NcpdpRawClaim> findByPayerIdAndStatus(String payerId, NcpdpStatus status);

    /**
     * Find claims by pharmacy ID and status
     *
     * @param pharmacyId the pharmacy identifier
     * @param status the processing status
     * @return list of claims matching both criteria
     */
    List<NcpdpRawClaim> findByPharmacyIdAndStatus(String pharmacyId, NcpdpStatus status);

    /**
     * Find claims created in the last N minutes with specific status.
     * Useful for monitoring and alerting.
     *
     * @param minutes number of minutes to look back
     * @param status the processing status
     * @return list of recent claims with the status
     */
    @Query("SELECT n FROM NcpdpRawClaim n WHERE n.status = :status " +
           "AND n.createdDate >= :cutoffTime")
    List<NcpdpRawClaim> findRecentByStatus(
        @Param("cutoffTime") LocalDateTime cutoffTime,
        @Param("status") NcpdpStatus status
    );

    /**
     * Find claims stuck in PROCESSING status for longer than threshold.
     * These might indicate processing failures that didn't update status.
     *
     * @param thresholdMinutes minutes threshold for "stuck" claims
     * @return list of stuck claims
     */
    @Query("SELECT n FROM NcpdpRawClaim n WHERE n.status = 'PROCESSING' " +
           "AND n.processingStartedDate < :cutoffTime")
    List<NcpdpRawClaim> findStuckProcessingClaims(
        @Param("cutoffTime") LocalDateTime cutoffTime
    );

    /**
     * Get processing statistics
     *
     * @return summary of claims by status
     */
    @Query("SELECT n.status, COUNT(n) FROM NcpdpRawClaim n GROUP BY n.status")
    List<Object[]> getStatusSummary();

    /**
     * Delete processed claims older than specified date.
     * Used for data retention cleanup.
     *
     * @param cutoffDate claims processed before this date will be deleted
     * @return number of claims deleted
     */
    @Query("DELETE FROM NcpdpRawClaim n WHERE n.status = 'PROCESSED' " +
           "AND n.processedDate < :cutoffDate")
    int deleteProcessedClaimsBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find claims by transaction ID
     *
     * @param transactionId the transaction identifier
     * @return list of claims with the transaction ID
     */
    List<NcpdpRawClaim> findByTransactionId(String transactionId);
}

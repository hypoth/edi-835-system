package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.ClaimProcessingLog;
import com.healthcare.edi835.entity.EdiFileBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ClaimProcessingLog entity.
 */
@Repository
public interface ClaimProcessingLogRepository extends JpaRepository<ClaimProcessingLog, UUID> {

    /**
     * Finds logs by claim ID.
     */
    List<ClaimProcessingLog> findByClaimId(String claimId);

    /**
     * Finds logs by bucket.
     */
    List<ClaimProcessingLog> findByBucket(EdiFileBucket bucket);

    /**
     * Finds logs by bucket ID.
     */
    @Query("SELECT c FROM ClaimProcessingLog c WHERE c.bucket.bucketId = :bucketId")
    List<ClaimProcessingLog> findByBucketId(@Param("bucketId") UUID bucketId);

    /**
     * Finds rejected claims.
     */
    @Query("SELECT c FROM ClaimProcessingLog c WHERE c.status = 'REJECTED' ORDER BY c.processedAt DESC")
    List<ClaimProcessingLog> findRejectedClaims();

    /**
     * Finds rejected claims for a specific payer.
     */
    @Query("SELECT c FROM ClaimProcessingLog c WHERE c.payerId = :payerId AND c.status = 'REJECTED'")
    List<ClaimProcessingLog> findRejectedClaimsByPayer(@Param("payerId") String payerId);

    /**
     * Finds claims processed today.
     */
    @Query("SELECT c FROM ClaimProcessingLog c WHERE c.processedAt >= :startOfDay")
    List<ClaimProcessingLog> findClaimsProcessedToday(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Counts rejected claims for today.
     */
    @Query("SELECT COUNT(c) FROM ClaimProcessingLog c WHERE c.status = 'REJECTED' AND c.processedAt >= :startOfDay")
    long countRejectedClaimsToday(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Gets rejection statistics by payer.
     */
    @Query("SELECT c.payerId, COUNT(c), COUNT(CASE WHEN c.status = 'REJECTED' THEN 1 END) " +
           "FROM ClaimProcessingLog c WHERE c.processedAt >= :since GROUP BY c.payerId")
    List<Object[]> getRejectionStatisticsByPayer(@Param("since") LocalDateTime since);

    /**
     * Gets top rejection reasons.
     */
    @Query("SELECT c.rejectionReason, COUNT(c) FROM ClaimProcessingLog c " +
           "WHERE c.status = 'REJECTED' AND c.rejectionReason IS NOT NULL " +
           "GROUP BY c.rejectionReason ORDER BY COUNT(c) DESC")
    List<Object[]> getTopRejectionReasons();

    /**
     * Counts all processed claims (all statuses).
     */
    @Query("SELECT COUNT(c) FROM ClaimProcessingLog c WHERE c.status IN ('PROCESSED', 'ACCEPTED')")
    long countProcessedClaims();

    /**
     * Counts all rejected claims.
     */
    @Query("SELECT COUNT(c) FROM ClaimProcessingLog c WHERE c.status = 'REJECTED'")
    long countRejectedClaims();
}

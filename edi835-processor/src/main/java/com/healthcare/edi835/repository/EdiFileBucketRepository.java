package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.EdiFileBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EdiFileBucket entity.
 * This is a CRITICAL repository with custom queries for bucket management.
 */
@Repository
public interface EdiFileBucketRepository extends JpaRepository<EdiFileBucket, UUID> {

    /**
     * Finds bucket by payer and payee IDs with ACCUMULATING status.
     * Uses pessimistic locking for thread safety.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM EdiFileBucket b WHERE b.payerId = :payerId AND b.payeeId = :payeeId AND b.status = 'ACCUMULATING'")
    Optional<EdiFileBucket> findAccumulatingBucketForPayerPayee(@Param("payerId") String payerId,
                                                                 @Param("payeeId") String payeeId);

    /**
     * Finds bucket by payer, payee, BIN, and PCN with ACCUMULATING status.
     * Used for BIN_PCN bucketing strategy.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM EdiFileBucket b WHERE b.payerId = :payerId AND b.payeeId = :payeeId " +
           "AND b.binNumber = :binNumber AND b.pcnNumber = :pcnNumber AND b.status = 'ACCUMULATING'")
    Optional<EdiFileBucket> findAccumulatingBucketForBinPcn(@Param("payerId") String payerId,
                                                             @Param("payeeId") String payeeId,
                                                             @Param("binNumber") String binNumber,
                                                             @Param("pcnNumber") String pcnNumber);

    /**
     * Finds all buckets with specific status.
     */
    List<EdiFileBucket> findByStatus(EdiFileBucket.BucketStatus status);

    /**
     * Finds all buckets pending approval.
     */
    @Query("SELECT b FROM EdiFileBucket b WHERE b.status = 'PENDING_APPROVAL' ORDER BY b.awaitingApprovalSince ASC")
    List<EdiFileBucket> findPendingApprovals();

    /**
     * Finds all active (accumulating) buckets.
     */
    @Query("SELECT b FROM EdiFileBucket b WHERE b.status = 'ACCUMULATING' ORDER BY b.createdAt DESC")
    List<EdiFileBucket> findActiveBuckets();

    /**
     * Finds buckets that need threshold evaluation.
     * Returns buckets in ACCUMULATING status that were last updated before the given time.
     */
    @Query("SELECT b FROM EdiFileBucket b WHERE b.status = 'ACCUMULATING' AND b.lastUpdated < :before")
    List<EdiFileBucket> findBucketsForThresholdEvaluation(@Param("before") LocalDateTime before);

    /**
     * Counts buckets by status.
     */
    long countByStatus(EdiFileBucket.BucketStatus status);

    /**
     * Finds buckets by payer ID.
     */
    List<EdiFileBucket> findByPayerId(String payerId);

    /**
     * Finds buckets by payee ID.
     */
    List<EdiFileBucket> findByPayeeId(String payeeId);

    /**
     * Finds buckets created today.
     */
    @Query("SELECT b FROM EdiFileBucket b WHERE b.createdAt >= :startOfDay")
    List<EdiFileBucket> findBucketsCreatedToday(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Gets summary statistics for dashboard.
     */
    @Query("SELECT b.status, COUNT(b), SUM(b.claimCount), SUM(b.totalAmount) " +
           "FROM EdiFileBucket b GROUP BY b.status")
    List<Object[]> getBucketStatistics();

    /**
     * Finds buckets by payer ID and status.
     */
    List<EdiFileBucket> findByPayerIdAndStatus(String payerId, EdiFileBucket.BucketStatus status);

    /**
     * Finds buckets by payee ID and status.
     */
    List<EdiFileBucket> findByPayeeIdAndStatus(String payeeId, EdiFileBucket.BucketStatus status);

    /**
     * Finds buckets with multiple statuses.
     */
    List<EdiFileBucket> findByStatusIn(List<EdiFileBucket.BucketStatus> statuses);

    /**
     * Finds recent buckets (limit by creation date).
     */
    @Query("SELECT b FROM EdiFileBucket b ORDER BY b.createdAt DESC")
    List<EdiFileBucket> findRecentBuckets();

    /**
     * Finds recent buckets with limit.
     */
    default List<EdiFileBucket> findRecentBuckets(int limit) {
        return findRecentBuckets().stream().limit(limit).toList();
    }

    /**
     * Finds stale buckets that have been accumulating for more than the specified days.
     *
     * @param days the number of days to consider a bucket stale
     * @return list of stale buckets
     */
    @Query("SELECT b FROM EdiFileBucket b WHERE b.status = 'ACCUMULATING' " +
           "AND b.createdAt < :before ORDER BY b.createdAt ASC")
    List<EdiFileBucket> findStaleBuckets(@Param("before") LocalDateTime before);

    /**
     * Finds stale buckets using days parameter.
     *
     * @param daysOld the number of days to consider a bucket stale
     * @return list of stale buckets
     */
    default List<EdiFileBucket> findStaleBuckets(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        return findStaleBuckets(cutoff);
    }

    /**
     * Finds buckets by payer and payee IDs.
     */
    @Query("SELECT b FROM EdiFileBucket b WHERE b.payerId = :payerId AND b.payeeId = :payeeId")
    List<EdiFileBucket> findByPayerIdAndPayeeId(@Param("payerId") String payerId,
                                                  @Param("payeeId") String payeeId);

    /**
     * Finds buckets by BIN number.
     */
    List<EdiFileBucket> findByBinNumber(String binNumber);
}

package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.CheckAuditLog;
import com.healthcare.edi835.entity.CheckPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for CheckAuditLog entity.
 * Provides comprehensive audit trail queries for check payment operations.
 */
@Repository
public interface CheckAuditLogRepository extends JpaRepository<CheckAuditLog, Long> {

    /**
     * Finds audit logs for a specific check payment.
     * Returns logs ordered by creation time (oldest first).
     *
     * @param checkPayment The check payment
     * @return List of audit logs for the check payment
     */
    List<CheckAuditLog> findByCheckPaymentOrderByCreatedAtAsc(CheckPayment checkPayment);

    /**
     * Finds audit logs by check number.
     * Useful for tracking check lifecycle across multiple operations.
     *
     * @param checkNumber The check number
     * @return List of audit logs for the check number
     */
    List<CheckAuditLog> findByCheckNumberOrderByCreatedAtAsc(String checkNumber);

    /**
     * Finds audit logs by bucket ID.
     * Returns all check-related operations for a bucket.
     *
     * @param bucketId The bucket ID
     * @return List of audit logs for the bucket
     */
    List<CheckAuditLog> findByBucketIdOrderByCreatedAtDesc(String bucketId);

    /**
     * Finds audit logs by action type.
     *
     * @param action The action (e.g., "ASSIGNED", "VOIDED")
     * @return List of audit logs with given action
     */
    List<CheckAuditLog> findByActionOrderByCreatedAtDesc(String action);

    /**
     * Finds audit logs performed by a specific user.
     *
     * @param performedBy User identifier
     * @return List of audit logs performed by the user
     */
    List<CheckAuditLog> findByPerformedByOrderByCreatedAtDesc(String performedBy);

    /**
     * Finds recent audit logs.
     *
     * @param since Timestamp to filter from
     * @return List of recent audit logs
     */
    @Query("SELECT cal FROM CheckAuditLog cal WHERE cal.createdAt >= :since " +
           "ORDER BY cal.createdAt DESC")
    List<CheckAuditLog> findRecentLogs(@Param("since") LocalDateTime since);

    /**
     * Finds audit logs by check payment ID.
     *
     * @param checkPaymentId Check payment ID
     * @return List of audit logs for the check payment
     */
    @Query("SELECT cal FROM CheckAuditLog cal WHERE cal.checkPayment.id = :checkPaymentId " +
           "ORDER BY cal.createdAt ASC")
    List<CheckAuditLog> findByCheckPaymentId(@Param("checkPaymentId") UUID checkPaymentId);

    /**
     * Finds voided checks within a time period.
     * Useful for reporting and compliance.
     *
     * @param startDate Start of time period
     * @param endDate End of time period
     * @return List of void audit logs
     */
    @Query("SELECT cal FROM CheckAuditLog cal WHERE cal.action = 'VOIDED' " +
           "AND cal.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY cal.createdAt DESC")
    List<CheckAuditLog> findVoidedChecksInPeriod(@Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Finds audit logs for a date range by action.
     *
     * @param action Action type
     * @param startDate Start date
     * @param endDate End date
     * @return List of audit logs
     */
    @Query("SELECT cal FROM CheckAuditLog cal WHERE cal.action = :action " +
           "AND cal.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY cal.createdAt DESC")
    List<CheckAuditLog> findByActionInPeriod(@Param("action") String action,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Counts audit logs by action type.
     *
     * @param action Action type
     * @return Count of logs with given action
     */
    long countByAction(String action);

    /**
     * Finds all audit logs for checks associated with a specific bucket.
     *
     * @param bucketId Bucket ID
     * @param limit Maximum number of results
     * @return List of audit logs (limited)
     */
    @Query(value = "SELECT cal FROM CheckAuditLog cal WHERE cal.bucketId = :bucketId " +
                   "ORDER BY cal.createdAt DESC")
    List<CheckAuditLog> findTopByBucketId(@Param("bucketId") String bucketId);

    /**
     * Finds system-initiated actions (performed by "SYSTEM").
     *
     * @return List of system-initiated audit logs
     */
    @Query("SELECT cal FROM CheckAuditLog cal WHERE cal.performedBy = 'SYSTEM' " +
           "ORDER BY cal.createdAt DESC")
    List<CheckAuditLog> findSystemInitiatedActions();

    /**
     * Gets audit trail summary for reporting.
     * Returns counts by action type for a time period.
     *
     * @param startDate Start date
     * @param endDate End date
     * @return List of action type counts
     */
    @Query("SELECT cal.action, COUNT(cal) FROM CheckAuditLog cal " +
           "WHERE cal.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY cal.action ORDER BY COUNT(cal) DESC")
    List<Object[]> getAuditSummary(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);
}

package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.BucketApprovalLog;
import com.healthcare.edi835.entity.EdiFileBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for BucketApprovalLog entity.
 */
@Repository
public interface BucketApprovalLogRepository extends JpaRepository<BucketApprovalLog, UUID> {

    /**
     * Finds approval logs by bucket.
     */
    List<BucketApprovalLog> findByBucketOrderByApprovedAtDesc(EdiFileBucket bucket);

    /**
     * Finds approvals by action type.
     */
    List<BucketApprovalLog> findByAction(BucketApprovalLog.ApprovalAction action);

    /**
     * Finds approvals by user.
     */
    List<BucketApprovalLog> findByApprovedBy(String approvedBy);

    /**
     * Finds recent approvals.
     */
    @Query("SELECT a FROM BucketApprovalLog a WHERE a.approvedAt >= :since ORDER BY a.approvedAt DESC")
    List<BucketApprovalLog> findRecentApprovals(@Param("since") LocalDateTime since);

    /**
     * Gets approval statistics by action.
     */
    @Query("SELECT a.action, COUNT(a) FROM BucketApprovalLog a GROUP BY a.action")
    List<Object[]> getApprovalStatistics();

    /**
     * Finds approval logs by bucket (without ordering).
     */
    List<BucketApprovalLog> findByBucket(EdiFileBucket bucket);
}

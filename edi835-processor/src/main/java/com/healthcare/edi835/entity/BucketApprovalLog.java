package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bucket Approval Log entity - audit trail for approval workflow.
 * Maps to 'bucket_approval_log' table in PostgreSQL.
 */
@Entity
@Table(name = "bucket_approval_log", indexes = {
        @Index(name = "idx_approval_log_bucket", columnList = "bucketId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketApprovalLog {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id")
    private EdiFileBucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ApprovalAction action;

    @Column(name = "approved_by", nullable = false, length = 100)
    private String approvedBy;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "scheduled_generation_time")
    private LocalDateTime scheduledGenerationTime;

    @Column(name = "approved_at")
    @Builder.Default
    private LocalDateTime approvedAt = LocalDateTime.now();

    public enum ApprovalAction {
        APPROVE,
        REJECT,
        OVERRIDE
    }

    /**
     * Creates an approval log entry.
     */
    public static BucketApprovalLog forApproval(EdiFileBucket bucket, String approvedBy,
                                                 String comments) {
        return BucketApprovalLog.builder()
                .bucket(bucket)
                .action(ApprovalAction.APPROVE)
                .approvedBy(approvedBy)
                .comments(comments)
                .build();
    }

    /**
     * Creates a rejection log entry.
     */
    public static BucketApprovalLog forRejection(EdiFileBucket bucket, String approvedBy,
                                                  String comments) {
        return BucketApprovalLog.builder()
                .bucket(bucket)
                .action(ApprovalAction.REJECT)
                .approvedBy(approvedBy)
                .comments(comments)
                .build();
    }

    /**
     * Creates an override log entry.
     */
    public static BucketApprovalLog forOverride(EdiFileBucket bucket, String approvedBy,
                                                 String comments) {
        return BucketApprovalLog.builder()
                .bucket(bucket)
                .action(ApprovalAction.OVERRIDE)
                .approvedBy(approvedBy)
                .comments(comments)
                .build();
    }
}

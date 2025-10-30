package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard summary for Operations Manager view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {

    // Summary metrics
    private Long totalBuckets;
    private Long activeBuckets;
    private Long pendingApprovalBuckets;
    private Long totalFiles;
    private Long pendingDeliveryFiles;
    private Long failedDeliveryFiles;
    private Long totalClaims;
    private Long processedClaims;
    private Long rejectedClaims;

    // Additional metrics
    private Integer activeBucketsCount;
    private Integer pendingApprovalsCount;
    private Integer totalClaimsProcessedToday;
    private BigDecimal totalAmountProcessedToday;
    private Integer filesGeneratedToday;
    private Integer rejectedClaimsToday;
    private Double rejectionRate;

    // Active buckets list
    private List<BucketSummaryDTO> activeBucketsList;

    // Pending approvals
    private List<BucketSummaryDTO> pendingApprovals;

    // Recent activity
    private List<RecentActivityDTO> recentActivity;

    // Rejection analytics
    private List<RejectionAnalyticsDTO> rejectionAnalytics;

    // Alerts
    private List<AlertDTO> alerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityDTO {
        private String activityType;  // FILE_GENERATED, BUCKET_APPROVED, BUCKET_REJECTED, etc.
        private String description;
        private String bucketId;
        private String fileName;
        private java.time.LocalDateTime timestamp;
        private String performedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectionAnalyticsDTO {
        private String payerId;
        private String payerName;
        private Integer rejectionCount;
        private Double rejectionRate;
        private String topRejectionReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertDTO {
        private String alertType;  // THRESHOLD_APPROACHING, APPROVAL_REQUIRED, DELIVERY_FAILED, etc.
        private String severity;   // INFO, WARNING, ERROR
        private String message;
        private String bucketId;
        private java.time.LocalDateTime timestamp;
    }
}

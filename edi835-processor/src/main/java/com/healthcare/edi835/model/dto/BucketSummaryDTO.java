package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bucket summary for dashboard and monitoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketSummaryDTO {

    private String bucketId;
    private String status;
    private String bucketingRuleName;
    private String payerId;
    private String payerName;
    private String payeeId;
    private String payeeName;
    private Integer claimCount;
    private BigDecimal totalAmount;
    private Integer rejectionCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private LocalDateTime awaitingApprovalSince;

    // Threshold information
    private Integer maxClaims;
    private BigDecimal maxAmount;
    private String timeDuration;

    // Progress indicators
    private Double claimCountProgress;  // Percentage of max claims
    private Double amountProgress;      // Percentage of max amount
}

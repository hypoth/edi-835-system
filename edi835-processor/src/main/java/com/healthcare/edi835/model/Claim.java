package com.healthcare.edi835.model;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Claim document from Cosmos DB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Container(containerName = "claims")
public class Claim {

    @Id
    private String id;

    @PartitionKey
    private String payerId;

    private String payeeId;
    private String claimNumber;
    private String patientId;
    private String patientName;
    
    // Insurance information
    private String binNumber;
    private String pcnNumber;
    
    // Claim details
    private LocalDate serviceDate;
    private LocalDate statementFromDate;
    private LocalDate statementToDate;
    
    // Financial information
    private BigDecimal totalChargeAmount;
    private BigDecimal paidAmount;
    private BigDecimal patientResponsibilityAmount;
    private BigDecimal adjustmentAmount;
    
    // Status
    private ClaimStatus status;
    private String statusReason;
    
    // Service lines
    private List<ServiceLine> serviceLines;
    
    // Adjustment information
    private List<ClaimAdjustment> adjustments;
    
    // Metadata
    private LocalDateTime processedDate;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;
    private String processedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLine {
        private String procedureCode;
        private String modifier;
        private BigDecimal chargedAmount;
        private BigDecimal paidAmount;
        private BigDecimal adjustmentAmount;
        private Integer units;
        private LocalDate serviceDate;
        private List<ServiceLineAdjustment> adjustments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimAdjustment {
        private String groupCode; // CO, PR, OA, PI
        private String reasonCode; // CARC codes
        private BigDecimal amount;
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLineAdjustment {
        private String groupCode;
        private String reasonCode;
        private BigDecimal amount;
    }

    public enum ClaimStatus {
        PROCESSED,
        PAID,
        DENIED,
        ADJUSTED,
        PENDING
    }
}
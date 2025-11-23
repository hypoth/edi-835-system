package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for check reservation information.
 * Represents a pre-allocated range of check numbers for auto-approval workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckReservationDTO {

    private String id;
    private String checkNumberStart;
    private String checkNumberEnd;
    private Integer totalChecks;
    private Integer checksUsed;
    private Integer checksRemaining;
    private Double usagePercentage;

    // Bank details
    private String bankName;
    private String routingNumber;
    private String accountNumberLast4;

    // References
    private String paymentMethodId;
    private String payerId;
    private String payerName;

    // Status
    private String status;  // ACTIVE, EXHAUSTED, CANCELLED

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    // Alert indicators
    private Boolean isLowStock;
    private Boolean isExhausted;
}

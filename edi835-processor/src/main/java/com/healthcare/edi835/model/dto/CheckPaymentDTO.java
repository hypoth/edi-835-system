package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for check payment information.
 * Used in API responses and frontend display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPaymentDTO {

    private String id;
    private String bucketId;
    private String checkNumber;
    private BigDecimal checkAmount;
    private LocalDate checkDate;
    private String bankName;
    private String routingNumber;
    private String accountNumberLast4;
    private String status;  // RESERVED, ASSIGNED, ACKNOWLEDGED, ISSUED, VOID, CANCELLED

    // Assignment tracking
    private String assignedBy;
    private LocalDateTime assignedAt;

    // Acknowledgment tracking
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;

    // Issuance tracking
    private String issuedBy;
    private LocalDateTime issuedAt;

    // Void tracking
    private String voidReason;
    private String voidedBy;
    private LocalDateTime voidedAt;

    // Metadata
    private String paymentMethodId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Additional computed fields
    private Boolean canBeVoided;
    private Integer hoursUntilVoidDeadline;
}

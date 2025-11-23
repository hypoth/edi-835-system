package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for check audit log entries.
 * Used in audit trail display and reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckAuditLogDTO {

    private Long id;
    private String checkPaymentId;
    private String checkNumber;
    private String action;        // ASSIGNED, ACKNOWLEDGED, ISSUED, VOIDED, etc.
    private String bucketId;
    private BigDecimal amount;
    private String performedBy;
    private String notes;
    private LocalDateTime createdAt;
}

package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for voiding a check payment.
 * Requires FINANCIAL_ADMIN role and valid reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoidCheckRequest {

    private String reason;      // Required: Reason for voiding
    private String voidedBy;    // Required: User voiding (must have FINANCIAL_ADMIN role)
}

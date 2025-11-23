package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for acknowledging a check payment.
 * User confirms the check amount before issuance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcknowledgeCheckRequest {

    private String acknowledgedBy;  // Required: User acknowledging the check
}

package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for marking a check as issued.
 * Records physical issuance/mailing of check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCheckRequest {

    private String issuedBy;  // Required: User marking check as issued
}

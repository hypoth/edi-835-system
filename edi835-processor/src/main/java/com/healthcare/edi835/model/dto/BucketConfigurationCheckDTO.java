package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for checking bucket configuration status.
 * Returns information about missing payer or payee configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketConfigurationCheckDTO {

    private String bucketId;
    private boolean hasAllConfiguration;
    private boolean payerExists;
    private boolean payeeExists;

    // Details for missing configuration
    private String missingPayerId;
    private String missingPayerName;
    private String missingPayeeId;
    private String missingPayeeName;

    // Suggested actions
    private String actionRequired;  // "CREATE_PAYER", "CREATE_PAYEE", "CREATE_BOTH", "NONE"
}

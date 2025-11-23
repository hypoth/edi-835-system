package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a check payment configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCheckPaymentConfigRequest {

    private String configValue;
    private String description;
    private Boolean isActive;
    private String updatedBy;
}

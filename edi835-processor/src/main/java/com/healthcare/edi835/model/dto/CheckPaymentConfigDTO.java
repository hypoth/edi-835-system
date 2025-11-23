package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for CheckPaymentConfig entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPaymentConfigDTO {

    private String id;
    private String configKey;
    private String configValue;
    private String description;
    private String valueType;  // STRING, INTEGER, BOOLEAN, EMAIL
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Helper for frontend display
    private String displayName;
    private boolean isValid;
}

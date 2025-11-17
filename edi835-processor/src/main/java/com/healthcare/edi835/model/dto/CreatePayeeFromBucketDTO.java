package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for creating a payee from bucket information.
 * Allows user to provide additional required fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayeeFromBucketDTO {

    @NotBlank(message = "Bucket ID is required")
    private String bucketId;

    // Payee identification (populated from bucket)
    @NotBlank(message = "Payee ID is required")
    private String payeeId;

    @NotBlank(message = "Payee name is required")
    private String payeeName;

    // Provider identifiers (user must provide)
    private String npi;
    private String taxId;

    // Address information (optional but recommended)
    private String addressStreet;
    private String addressCity;
    private String addressState;
    private String addressZip;

    // Metadata
    private Boolean requiresSpecialHandling = false;
    private Boolean isActive = true;
    private String createdBy;
}

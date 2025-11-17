package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for creating a payer from bucket information.
 * Allows user to provide additional required fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayerFromBucketDTO {

    @NotBlank(message = "Bucket ID is required")
    private String bucketId;

    // Payer identification (populated from bucket)
    @NotBlank(message = "Payer ID is required")
    private String payerId;

    @NotBlank(message = "Payer name is required")
    private String payerName;

    // EDI identifiers (user must provide)
    @NotBlank(message = "ISA sender ID is required")
    private String isaSenderId;

    private String isaQualifier = "ZZ";  // Default to ZZ
    private String gsApplicationSenderId;

    // Address information (optional)
    private String addressStreet;
    private String addressCity;
    private String addressState;
    private String addressZip;

    // SFTP configuration (optional)
    private String sftpHost;
    private Integer sftpPort;
    private String sftpUsername;
    private String sftpPassword;
    private String sftpPath;

    // Metadata
    private Boolean requiresSpecialHandling = false;
    private Boolean isActive = true;
    private String createdBy;
}

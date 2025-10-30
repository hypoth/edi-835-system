package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for file download operations.
 * Contains file metadata and content without using Hibernate entity mapping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadDTO {
    private String fileName;
    private Long fileSizeBytes;
    private byte[] fileContent;
}

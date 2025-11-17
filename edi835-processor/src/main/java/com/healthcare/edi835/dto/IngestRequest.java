package com.healthcare.edi835.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for ingesting NCPDP claims from a file
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequest {

    /**
     * Path to the NCPDP claims file
     */
    private String filePath;

    /**
     * Whether to stop on first error (default: false)
     */
    private boolean stopOnError;
}

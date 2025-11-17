package com.healthcare.edi835.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of NCPDP claim ingestion operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionResult {

    /**
     * Total number of transactions processed
     */
    private int totalProcessed;

    /**
     * Number of successfully inserted claims
     */
    private int totalSuccess;

    /**
     * Number of failed insertions
     */
    private int totalFailed;

    /**
     * Overall status (SUCCESS, PARTIAL, FAILED)
     */
    private String status;

    /**
     * List of error messages
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Timestamp of ingestion
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Creates a success result
     */
    public static IngestionResult success(int count) {
        return IngestionResult.builder()
            .totalProcessed(count)
            .totalSuccess(count)
            .totalFailed(0)
            .status("SUCCESS")
            .build();
    }

    /**
     * Creates a failure result
     */
    public static IngestionResult failure(String errorMessage) {
        return IngestionResult.builder()
            .totalProcessed(0)
            .totalSuccess(0)
            .totalFailed(0)
            .status("FAILED")
            .errors(List.of(errorMessage))
            .build();
    }

    /**
     * Adds an error message
     */
    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}

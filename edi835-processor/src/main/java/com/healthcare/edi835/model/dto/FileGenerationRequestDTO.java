package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for force file generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileGenerationRequestDTO {

    private String bucketId;
    private List<UUID> bucketIds; // For bulk generation
    private Boolean forceGeneration;
    private String requestedBy;
    private String reason;

    // Wrapper method for backward compatibility
    public List<UUID> getBucketIds() {
        if (bucketIds != null && !bucketIds.isEmpty()) {
            return bucketIds;
        }
        // If bucketIds is null but bucketId is provided, return single-item list
        if (bucketId != null) {
            return List.of(UUID.fromString(bucketId));
        }
        return List.of();
    }
}

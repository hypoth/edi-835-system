package com.healthcare.edi835.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for EDI Generation Thresholds.
 * Used to transfer threshold data without exposing JPA entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationThresholdDTO {

    private UUID id;
    private String thresholdName;
    private String thresholdType;
    private Integer maxClaims;
    private BigDecimal maxAmount;
    private String timeDuration;
    private String generationSchedule;

    // Instead of full entity object, just include ID and name
    private UUID linkedBucketingRuleId;
    private String linkedBucketingRuleName;

    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    /**
     * Backward compatibility: Expose 'thresholdId' as alias for 'id' in JSON responses.
     * This ensures clients expecting 'thresholdId' field will work correctly.
     */
    @JsonProperty("thresholdId")
    public UUID getThresholdId() {
        return this.id;
    }

    /**
     * Backward compatibility: Accept 'thresholdId' during deserialization.
     */
    @JsonProperty("thresholdId")
    public void setThresholdId(UUID thresholdId) {
        this.id = thresholdId;
    }
}

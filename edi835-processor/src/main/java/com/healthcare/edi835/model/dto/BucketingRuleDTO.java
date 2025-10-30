package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for EDI Bucketing Rules.
 * Used to transfer bucketing rule data without exposing JPA entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketingRuleDTO {

    private UUID id;
    private String ruleName;
    private String ruleType;
    private String groupingExpression;
    private Integer priority;

    // Instead of full entity objects, just include IDs
    private UUID linkedPayerId;
    private String linkedPayerName;
    private UUID linkedPayeeId;
    private String linkedPayeeName;

    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}

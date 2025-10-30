package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EDI File Naming Template entity - defines file naming patterns.
 * Maps to 'edi_file_naming_templates' table in PostgreSQL.
 */
@Entity
@Table(name = "edi_file_naming_templates", indexes = {
        @Index(name = "idx_naming_templates_default", columnList = "isDefault"),
        @Index(name = "idx_naming_templates_rule", columnList = "linkedBucketingRuleId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdiFileNamingTemplate {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "template_name", unique = true, nullable = false)
    private String templateName;

    @Column(name = "template_pattern", nullable = false, length = 500)
    private String templatePattern;

    @Column(name = "date_format", length = 50)
    @Builder.Default
    private String dateFormat = "yyyyMMdd";

    @Column(name = "time_format", length = 50)
    @Builder.Default
    private String timeFormat = "HHmmss";

    @Column(name = "sequence_padding")
    @Builder.Default
    private Integer sequencePadding = 4;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_conversion", length = 20)
    @Builder.Default
    private CaseConversion caseConversion = CaseConversion.UPPER;

    @Column(name = "separator_type", length = 10)
    @Builder.Default
    private String separatorType = "_";

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_bucketing_rule_id")
    private EdiBucketingRule linkedBucketingRule;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public enum CaseConversion {
        UPPER,
        LOWER,
        NONE
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Wrapper methods for backward compatibility
    public void setTemplateId(UUID templateId) {
        this.id = templateId;
    }

    public UUID getTemplateId() {
        return this.id;
    }
}

package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/**
 * Adjustment Code Mapping entity - CARC/RARC reference data.
 * Maps to 'adjustment_code_mapping' table in PostgreSQL.
 */
@Entity
@Table(name = "adjustment_code_mapping",
       uniqueConstraints = @UniqueConstraint(columnNames = {"codeType", "code"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentCodeMapping {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_type", nullable = false, length = 10)
    private CodeType codeType;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "group_code", length = 2)
    private String groupCode;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    public enum CodeType {
        CARC,  // Claim Adjustment Reason Code
        RARC   // Remittance Advice Remark Code
    }
}

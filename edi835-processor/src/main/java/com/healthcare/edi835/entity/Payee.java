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
 * Payee entity - stores payee/provider configuration.
 * Maps to 'payees' table in PostgreSQL.
 */
@Entity
@Table(name = "payees", indexes = {
        @Index(name = "idx_payees_payee_id", columnList = "payeeId"),
        @Index(name = "idx_payees_active", columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payee {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payee_id", unique = true, nullable = false, length = 50)
    private String payeeId;

    @Column(name = "payee_name", nullable = false)
    private String payeeName;

    @Column(name = "npi", length = 10)
    private String npi;

    @Column(name = "tax_id", length = 20)
    private String taxId;

    @Column(name = "address_street")
    private String addressStreet;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_state", length = 2)
    private String addressState;

    @Column(name = "address_zip", length = 10)
    private String addressZip;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

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

    @Column(name = "requires_special_handling")
    @Builder.Default
    private Boolean requiresSpecialHandling = false;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Wrapper method for backward compatibility
    public Boolean getRequiresSpecialHandling() {
        return requiresSpecialHandling != null ? requiresSpecialHandling : false;
    }
}

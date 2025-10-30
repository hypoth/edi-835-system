package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payer entity - stores payer configuration for EDI 835 generation.
 * Maps to 'payers' table in PostgreSQL.
 */
@Entity
@Table(name = "payers", indexes = {
        @Index(name = "idx_payers_payer_id", columnList = "payerId"),
        @Index(name = "idx_payers_active", columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payer {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payer_id", unique = true, nullable = false, length = 50)
    @NotBlank(message = "Payer ID is required")
    @Size(max = 50, message = "Payer ID must not exceed 50 characters")
    private String payerId;

    @Column(name = "payer_name", nullable = false)
    @NotBlank(message = "Payer name is required")
    private String payerName;

    @Column(name = "isa_qualifier", length = 2)
    @Size(max = 2, message = "ISA Qualifier must be exactly 2 characters")
    @Builder.Default
    private String isaQualifier = "ZZ";

    @Column(name = "isa_sender_id", nullable = false, length = 15)
    @NotBlank(message = "ISA Sender ID is required for EDI 835 generation")
    @Size(max = 15, message = "ISA Sender ID must not exceed 15 characters")
    private String isaSenderId;

    @Column(name = "gs_application_sender_id", length = 15)
    @Size(max = 15, message = "GS Application Sender ID must not exceed 15 characters")
    private String gsApplicationSenderId;

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

    // SFTP Configuration fields
    @Column(name = "sftp_host")
    private String sftpHost;

    @Column(name = "sftp_port")
    private Integer sftpPort;

    @Column(name = "sftp_username")
    private String sftpUsername;

    @Column(name = "sftp_password")
    private String sftpPassword;

    @Column(name = "sftp_path")
    private String sftpPath;

    @Column(name = "requires_special_handling")
    @Builder.Default
    private Boolean requiresSpecialHandling = false;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Wrapper methods for backward compatibility
    public Boolean getRequiresSpecialHandling() {
        return requiresSpecialHandling != null ? requiresSpecialHandling : false;
    }
}

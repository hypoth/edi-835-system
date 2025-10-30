package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * File Generation History entity - tracks generated EDI 835 files.
 * Maps to 'file_generation_history' table in PostgreSQL.
 */
@Entity
@Table(name = "file_generation_history", indexes = {
        @Index(name = "idx_file_history_bucket", columnList = "bucketId"),
        @Index(name = "idx_file_history_status", columnList = "deliveryStatus"),
        @Index(name = "idx_file_history_date", columnList = "generatedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileGenerationHistory {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id")
    private EdiFileBucket bucket;

    @Column(name = "generated_file_name", nullable = false, length = 500)
    private String generatedFileName;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "claim_count", nullable = false)
    private Integer claimCount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "generated_at")
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "generated_by", length = 100)
    private String generatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 50)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "delivery_attempt_count")
    @Builder.Default
    private Integer deliveryAttemptCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_content", columnDefinition = "TEXT")
    private byte[] fileContent;

    public enum DeliveryStatus {
        PENDING,
        DELIVERED,
        FAILED,
        RETRY
    }

    /**
     * Marks file as successfully delivered.
     */
    public void markDelivered() {
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * Marks delivery as failed with error message.
     */
    public void markFailed(String errorMessage) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Increments delivery attempt count and sets retry status.
     */
    public void incrementRetryAttempt() {
        this.deliveryAttemptCount++;
        this.deliveryStatus = DeliveryStatus.RETRY;
    }

    // Convenience/alias methods for backward compatibility

    /**
     * Alias for markFailed(). Marks delivery as failed.
     */
    public void markDeliveryFailed(String errorMessage) {
        markFailed(errorMessage);
    }

    /**
     * Alias for incrementRetryAttempt(). Increments retry count.
     */
    public void incrementRetryCount() {
        incrementRetryAttempt();
    }

    /**
     * Alias for generatedFileName. Gets the file name.
     */
    public String getFileName() {
        return this.generatedFileName;
    }

    /**
     * Alias for deliveryAttemptCount. Gets retry count.
     */
    public Integer getRetryCount() {
        return this.deliveryAttemptCount;
    }

    /**
     * Alias for id. Gets the file ID.
     */
    public UUID getFileId() {
        return this.id;
    }
}

package com.healthcare.edi835.model.projection;

import com.healthcare.edi835.entity.FileGenerationHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection interface for FileGenerationHistory summary data.
 * Excludes file_content to avoid LOB issues with SQLite JDBC driver.
 *
 * <p>This projection is used for list endpoints where file content is not needed,
 * ensuring compatibility with both SQLite and PostgreSQL.</p>
 *
 * <p>Field names match frontend TypeScript interface for direct JSON mapping.</p>
 */
public interface FileGenerationSummary {

    UUID getFileId();

    String getFileName();

    String getFilePath();

    Long getFileSizeBytes();

    Integer getClaimCount();

    BigDecimal getTotalAmount();

    LocalDateTime getGeneratedAt();

    String getGeneratedBy();

    FileGenerationHistory.DeliveryStatus getDeliveryStatus();

    LocalDateTime getDeliveredAt();

    Integer getRetryCount();

    String getErrorMessage();

    /**
     * Gets bucket ID through nested projection.
     */
    BucketSummary getBucket();

    /**
     * Nested projection for bucket information.
     */
    interface BucketSummary {
        UUID getBucketId();
        String getPayerId();
        String getPayerName();
        String getPayeeId();
        String getPayeeName();
    }
}

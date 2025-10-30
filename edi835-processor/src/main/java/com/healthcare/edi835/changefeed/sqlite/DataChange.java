package com.healthcare.edi835.changefeed.sqlite;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing a data change in the SQLite change feed.
 *
 * <p>This entity captures INSERT, UPDATE, and DELETE operations on tracked tables,
 * storing both old and new values as JSON for flexible change tracking.</p>
 *
 * <p>The version-based approach allows the same changes to be processed multiple
 * times by incrementing the feed_version, enabling replay and testing scenarios.</p>
 */
@Entity
@Table(name = "data_changes", indexes = {
    @Index(name = "idx_data_changes_version", columnList = "feed_version,sequence_number"),
    @Index(name = "idx_data_changes_processed", columnList = "processed,feed_version"),
    @Index(name = "idx_data_changes_table", columnList = "table_name,changed_at"),
    @Index(name = "idx_data_changes_row", columnList = "table_name,row_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_id")
    private Long changeId;

    @Column(name = "feed_version", nullable = false)
    @Builder.Default
    private Integer feedVersion = 1;

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 10)
    private OperationType operation;

    @Column(name = "row_id", nullable = false, length = 255)
    private String rowId;

    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;

    @Column(name = "changed_at")
    @Builder.Default
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "processed")
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Enum for database operation types.
     */
    public enum OperationType {
        INSERT,
        UPDATE,
        DELETE
    }

    /**
     * Marks this change as processed.
     */
    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * Marks this change as failed with an error message.
     *
     * @param errorMessage the error message
     */
    public void markAsFailed(String errorMessage) {
        this.processed = false;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }
}
